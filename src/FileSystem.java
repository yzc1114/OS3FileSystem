import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class FileSystem {

    public byte fileEnd = (byte)0xFF;

    //4kB
    public int clusterCapacity = 1024 * 4; // 2 ^ 12

    //256MB
    public int hardDiskCapacity = 1024 * 1024 * 256; //256MB 2 ^ 28

    //簇的个数 = 2 ^ 16
    public int numOfCluster = hardDiskCapacity / clusterCapacity;

    //FAT表的大小，为硬盘容量除以簇的大小，即得到簇的个数，之后乘以每个簇在FAT表中占的大小
    //由于我们设定的硬盘容量为256MB，每个簇的大小为4kB，所以硬盘分为1024 * 64个簇，为2的16次方。
    //所以用来表示簇号的FAT表项长度最少为16位，也就是两个字节。
    //所以，经过计算，FAT至少需要2的16次方个字节的容量
    //该FAT表需要两个簇来表示。
    public int FATCapacity = numOfCluster * 2; //这里的2是两个字节的意思，0xFFFF表示文件终止符。

    //存放FAT的表，大小为FATCapacity确定
    public byte[] FAT = new byte[FATCapacity];

    //由于需要经常找到FAT中的空簇号，我们需要用循环扫描算法
    //那么我们需要定义一个循环的扫描指针
    public int FATLoopScanIndex = 0;

    //规定空簇号在FAT中为0x00，根目录簇号规定为0x01
    public int rootClusterNum = 1;

    //实际存储的数据区
    public byte[] contents = new byte[this.hardDiskCapacity];

    //目录项大小 32 Byte
    public int catalogLength = 32;

    //每个簇的目录项个数
    public int numOfCatalogsInEachCluster = clusterCapacity / catalogLength;

    public FileSystem(){
        initSystem();
    }

    //初始化文件系统。
    public void initSystem(){
        //第一个簇号不保存任何东西，因为0x00表示空簇，所以这两个字节无用。
        this.FAT[0] = fileEnd;
        this.FAT[1] = fileEnd;
        //我们先把根目录的下一簇号设为-1，即表示根目录簇未满。
        this.FAT[2] = fileEnd;
        this.FAT[3] = fileEnd;
    }

    //从物理硬盘中，读取保存的数据。若数据不存在，则创建一个新的文件。
    public void loadFromDisk(){
        //从硬盘读取数据，如果不存在，则创建该文件。
        try{
            File data = new File("data");
            if(!data.exists()){
                System.out.println("实际硬盘中不存在该文件系统的数据，将创建新文件！\n");
                return;
            }
            System.out.println("正在读取物理硬盘中所存储的该文件系统的数据！\n");
            FileInputStream fis = new FileInputStream(data);
            fis.read(FAT, 0, FATCapacity);
            fis.read(contents, 0, hardDiskCapacity);
            System.out.println("读取完毕！\n");
            fis.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    //将目前存放在内存的文件系统数据，写入硬盘中的文件当中。
    public void saveToDisk(){
        try{
            File data = new File("data");
            if(!data.exists()){
                data.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(data);
            System.out.println("写入硬盘开始！");
            fos.write(FAT);
            fos.write(contents);
            System.out.println("写入硬盘结束！");
            fos.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    //添加一个文件或文件夹。
    private boolean addItem(String dirPath, String name, Catalog.ItemType itemType, byte[] contents){
        if(itemType == Catalog.ItemType.file && contents == null){
                //System.out.println("文件contents为null");
                return false;
        }

        int currClusterNum = getClusterNumBaseOnDirectoryPath(dirPath);
        if(currClusterNum == -1){
            return false;
        }
        //若运行到这里，则说明找到了这个文件夹，那么我们需要在里面添加一个新的文件或文件夹了
        while(true){
            for(int i = 0; i < numOfCatalogsInEachCluster; i++){
                Catalog catalog = getCatalogBaseOnCatalogIndexAndClusterIndex(currClusterNum, i);
                if(catalog == null){
                    return false;
                }
                if(!catalog.valid){
                    //进入此块表明已经找到为空的目录项，我们可以把最新的文件放进去。
                    //TODO: 首先找到一个簇号，将FAT中的该簇对应内容置为0xFFFF(两个字节，注意)
                    int targetClusterNum; //这个变量将表示我们新建文件的起始簇号。
                    targetClusterNum = findEmptyClusterNumber();
                    setFATItemBaseOnClusterNumAndInt(targetClusterNum, 0xFFFF);
                    //TODO: 然后建立它的目录项
                    Catalog newCatalog;
                    if(itemType == Catalog.ItemType.file){
                        newCatalog = new Catalog(name, Catalog.ItemType.file, contents.length, targetClusterNum, true);
                    }else{
                        newCatalog = new Catalog(name, Catalog.ItemType.subDirectory, 0, targetClusterNum, true);
                    }
                    //TODO: 然后将目录项保存在刚才找到的空目录项中。
                    //注意这里是存之前找到的空目录项，所以簇号是之前的currClusterNum，而不是用来保存文件的targetClusterNum。
                    setCatalogInContentsBaseOnCatalogAndClusterIndex(currClusterNum, i, newCatalog);
                    //TODO: 然后将文件的content保存到对应的簇中。
                    //我们定义的这个函数，将簇满时的操作封装起来了，我们只需要给他提供起始簇号就可以了
                    //分情况，如果是文件夹，则无需进行下一步。
                    if(itemType == Catalog.ItemType.file)
                        saveFileContentToClusterAndAppendClustersIfNeeded(contents, targetClusterNum);
                    return true;
                }
            }
            //若运行到这里，说明当前的存储目录项的簇已经满了，说明当前文件夹内存储的文件夹和文件的目录项太多了
            //我们需要寻找存储目录项的当前簇号的链接的下一个簇号
            int nextClusterNum = findTheLinkedClusterNumber(currClusterNum);
            if(nextClusterNum == 0){
                //若为0，则表明当前簇号不在使用，出了Bug
                //System.out.println("当前簇号在FAT中对应的内容为0x00，出了bug");
                return false;
            }
            if(nextClusterNum == -1){
                //若为-1，表明我们需要新分配一个簇给这个文件夹。
                int newClusterNum = findEmptyClusterNumber();
                if(newClusterNum == -1){
                    return false;
                }
                //链接上最新的簇号，在FAT表中
                setFATItemBaseOnClusterNumAndInt(currClusterNum, newClusterNum);
                //将最新的簇号在FAT中设置为末尾。
                setFATItemBaseOnClusterNumAndInt(newClusterNum, 0xFFFF);
                //将最新的簇号赋值给当前簇的下一簇号
                nextClusterNum = newClusterNum;
            }
            //更改当前的簇号为最新簇号。进入下一个循环。
            currClusterNum = nextClusterNum;
        }
    }

    //添加文件的接口。
    public boolean addFile(String dirPath, String name, byte[] contents){
        return this.addItem(dirPath, name, Catalog.ItemType.file, contents);
    }

    //添加文件夹的接口。
    public boolean addDir(String dirPath, String name){
        return this.addItem(dirPath, name, Catalog.ItemType.subDirectory, null);
    }

    //删除子目录
    public boolean deleteSubDirectory(String dirPath, String dirName){
        ArrayList<Catalog> catalogs = getAllCatalogsFromDirPath(dirPath);
        if(catalogs == null){
            return false;
        }
        Catalog targetDirCatalog = null;
        for(Catalog catalog : catalogs){
            if(catalog.fileName.equals(dirName)){
                targetDirCatalog = catalog;
                break;
            }
        }
        if(targetDirCatalog == null){
            System.out.println("没有找到该文件夹！");
            return false;
        }
        //此方法应该是递归的
        //先将现在的文件夹内部的全部信息删除。
        //是递归的方法，若找到了子文件夹，则递归调用本方法。若是文件，则直接将该文件的内容簇号全部置为空，然后将这个文件所在的目录项置为invalid的目录项。
        String fullPath = dirPath + dirName;
        ArrayList<Catalog> targetCatalogs = getAllCatalogsFromDirPath(fullPath);
        if(targetCatalogs == null){
            System.out.println("路径出错，请debug");
            return false;
        }
        for(Catalog catalog: targetCatalogs){
            if(catalog.itemType == Catalog.ItemType.subDirectory){
                //递归删除子文件夹。
                if(catalog.fileName.equals(".") || catalog.fileName.equals("..")){
                    continue;
                }
                deleteSubDirectory(dirPath + "/" + dirName, catalog.fileName);
            }else{
                //直接删除文件。
                deleteFileBaseOnFileCatalog(catalog);
            }
        }
        //删除完文件夹内部的信息之后，将本文件夹在父目录中的目录项信息置为invalid
        setCatalogInContentsBaseOnCatalogAndClusterIndex(targetDirCatalog.inWhichCluster, targetDirCatalog.catalogIndex, Catalog.getInvalidCatalog());
        //在FAT表中注销注册。
        //找到当前文件夹的起始簇号.
        removeFATLinkFrom(targetDirCatalog.clusterNum);
        return true;
    }

    //删除文件
    public boolean deleteFile(String dirPath, String fileName){
        //根据路径，删除一个文件。
        ArrayList<Catalog> catalogs = getAllCatalogsFromDirPath(dirPath);
        if(catalogs == null){
            return false;
        }
        Catalog targetCatalog = null;
        for(Catalog catalog : catalogs){
            if(catalog.fileName.equals(fileName)){
                if(catalog.itemType == Catalog.ItemType.subDirectory){
                    System.out.println("没有找到该文件！");
                    return false;
                }
                targetCatalog = catalog;
                break;
            }
        }
        if(targetCatalog == null){
            System.out.println("没有找到该文件！");
            return false;
        }
        //运行到此处说明找到了文件，我们把它删除。
        deleteFileBaseOnFileCatalog(targetCatalog);

        return false;
    }

    //根据描述文件的目录项删除文件的内容，配合deleteFile使用。
    private void deleteFileBaseOnFileCatalog(Catalog fileCatalog){
        //执行删除文件的操作。
        //首先找到文件内容的起始簇号。
        //然后将FAT表中链接的簇号全部置0，为不使用状态。
        assert fileCatalog.itemType == Catalog.ItemType.file;
        assert fileCatalog.inWhichCluster != 0;

        removeFATLinkFrom(fileCatalog.clusterNum);
        //之后在原来的目录项内，设置目录项为invalid的目录项
        setCatalogInContentsBaseOnCatalogAndClusterIndex(fileCatalog.inWhichCluster, fileCatalog.catalogIndex, Catalog.getInvalidCatalog());
    }

    //从startClusterNum簇号开始，删除所有在FAT表中链接的簇，将他们置为未使用。
    private void removeFATLinkFrom(int startClusterNum){
        //这个函数用来将以参数startClusterNum为首的FAT链表全部置为空
        int nextClusterNum = findTheLinkedClusterNumber(startClusterNum);
        setFATItemBaseOnClusterNumAndInt(startClusterNum, 0x0000);
        while(nextClusterNum != -1){
            int t = findTheLinkedClusterNumber(nextClusterNum);
            setFATItemBaseOnClusterNumAndInt(nextClusterNum, 0x0000);
            nextClusterNum = t;
        }
    }

    //根据文件夹的完整目录，获取这个文件夹包含的所有目录项。
    private ArrayList<Catalog> getAllCatalogsFromDirPath(String dirPath){
        int clusterNum = getClusterNumBaseOnDirectoryPath(dirPath);
        if(clusterNum == -1){
            return null;
        }
        ArrayList<Catalog> catalogs = new ArrayList<>();
        while (true){
            for(int i = 0; i < numOfCatalogsInEachCluster; i++){
                Catalog catalog = getCatalogBaseOnCatalogIndexAndClusterIndex(clusterNum, i);
                if(catalog == null){
                    return null;
                }
                catalog.inWhichCluster = clusterNum;
                catalog.catalogIndex = i;
                if(catalog.valid){
                    catalogs.add(catalog);
                }
            }

            //进入此块说明我们loop over了整个cluster，但是还没找到一个空的目录项
            //意思是说，这个簇的目录项已经存满了
            //我们去找当前簇链接的下一个簇
            int newClusterNum = findTheLinkedClusterNumber(clusterNum);
            if(newClusterNum == -1){
                //如果是-1，那么代表用完了，没有下一个簇了。我们直接break;
                break;
            }else if(newClusterNum == 0){
                //如果是0，那么出了bug，返回false，然后打印错误。
                //System.out.println("簇的链接号为0，出bug了");
                return null;
            }else{
                //正常情况，将新的号赋值给旧的号。之后继续循环
                clusterNum = newClusterNum;
            }
        }
        //运行到这里说明找完了全部的catalogs。
        return catalogs;
    }

    //列出dirPath内的所有文件和文件夹。
    public boolean listDir(String dirPath){
        //TODO: 列出dirPath内的所有文件和文件夹。
        ArrayList<Catalog> catalogs = getAllCatalogsFromDirPath(dirPath);
        if(catalogs == null){
            System.out.println("路径不存在！");
            return false;
        }
        System.out.println("\n当前路径：" + dirPath);
        //运行到这里说明找完了全部的文件夹和文件名。
        for(Catalog catalog : catalogs){
            String res = "";
            if(catalog.itemType == Catalog.ItemType.file){
                res += ("类型：文件 文件名：" + catalog.fileName + " 文件大小：" + catalog.fileLengthOfBytes + "B 起始簇号：" + catalog.clusterNum);
            }else{
                res += ("类型：文件夹 文件夹名：" + catalog.fileName + " 起始簇号：" + catalog.clusterNum);
            }
            System.out.println(res);
        }
        System.out.println();
        return true;
    }

    //根据文件夹的目录，获取这个文件夹的起始簇号。
    public int getClusterNumBaseOnDirectoryPath(String dirPath){
        //根据文件夹路径获得簇号
        //注意：dirPath的首字符不应该是/。
        assert dirPath.charAt(0) == '/';
        int currClusterNum = 1; //根目录簇号，为0
        String[] directories = dirPath.split("/");
        //若为根目录，split的长度会为0
        if(directories.length == 0)
            return currClusterNum;
        //若不是根目录，起始的第一项会为长度为零的字符串。
        directories = Arrays.copyOfRange(directories, 1, directories.length);
        for(int j = 0; j < directories.length; j++){
            //若字符串为空，则跳过，相当于什么都没有做
            String d = directories[j];
            if(d.equals(""))
                continue;
            //从根目录开始一步一步找这个文件夹
            //若发现没有该文件夹或者 不是文件夹而是文件，则返回错误

            //goDeeper 变量标志，是否找到当前的文件夹，若找到了，我们需要再向这个当前文件夹所保管的目录项进发
            //寻找下一级的目录
            boolean goDeeper = false;
            for(int i = 0; i < numOfCatalogsInEachCluster; i++){
                byte[] catalogBytes = Arrays.copyOfRange(contents, currClusterNum * clusterCapacity + i * catalogLength,
                        currClusterNum * clusterCapacity + (i + 1) * catalogLength);
                Catalog catalog = Catalog.parseCatalogBytes(catalogBytes);
                if(catalog == null){
                    //System.out.println("catalog is null");
                    return -1;
                }
                if(catalog.fileName.equals(d)){
                    if(catalog.itemType == Catalog.ItemType.file){
                        //System.out.println("不是文件夹，而是文件");
                        return -1;
                    }else{
                        //进入此段说明找到了这个文件夹，
                        //那么如果现在是最后一个目录，则获取他的目标簇号，并return
                        //若不是最后一个，就继续下一轮。
                        currClusterNum = catalog.clusterNum;
                        if(j == directories.length - 1)
                            return currClusterNum;
                        else{
                            goDeeper = true;
                            break;
                        }
                    }
                }
            }
            if(goDeeper)
                continue;
            //若循环完毕，仍没有找到目标文件夹，则前往当前簇号在FAT表中的下一个簇
            //若下一个表项为空，则说明这个文件夹不存在
            int nextClusterNum = 0;
            if(FAT[2 * currClusterNum] == (byte)0xFF && FAT[2 * currClusterNum + 1] == (byte)0xFF){
                //进入此块说明下一块不存在
                return -1;
            }
            nextClusterNum |= FAT[2 * currClusterNum] << 8;
            nextClusterNum |= FAT[2 * currClusterNum + 1];
            currClusterNum = nextClusterNum;
        }
        return currClusterNum;
    }

    //检查这个路径下的某文件或文件夹是否存在。
    public boolean checkIfPathExists(String dirPath, String name){
        //这个函数检索在dirPath索引下的，以name为名字的目录项是否存在
        //name可以是文件夹名，也可以是文件名
        int fatherClusterNum = getClusterNumBaseOnDirectoryPath(dirPath);
        if(fatherClusterNum == -1){
            return false;
        }
        ArrayList<Catalog> catalogs = getAllCatalogsFromDirPath(dirPath);
        if(catalogs == null)
            return false;
        for(Catalog c : catalogs){
            if(c.fileName.equals(name))
                return true;
        }
        return false;
    }

    //根据簇号设置FAT表的表项内容为content。
    private void setFATItemBaseOnClusterNumAndInt(int clusterNum, int content){
        //根据簇号，以及一个整数的低16位来设置一个FAT表项的内容
        int FATIndex = clusterNum * 2;
        FAT[FATIndex] = (byte)(content >> 8);
        FAT[FATIndex + 1] = (byte)(content);
    }

    //根据簇号，以及簇内偏移，获得目标目录项。
    private Catalog getCatalogBaseOnCatalogIndexAndClusterIndex(int currClusterNum, int catalogIndex){
        //根据簇号，簇内的目录项索引，获取这个目录项的对象。
        byte[] catalogBytes = Arrays.copyOfRange(contents, currClusterNum * clusterCapacity + catalogIndex * catalogLength,
                currClusterNum * clusterCapacity + (catalogIndex + 1) * catalogLength);
        Catalog catalog = Catalog.parseCatalogBytes(catalogBytes);
        if(catalog == null){
            //System.out.println("catalog is null");
            return null;
        }
        return catalog;
    }

    //根据簇号，簇内偏移，设置该目录项内容为新的目录项。
    private void setCatalogInContentsBaseOnCatalogAndClusterIndex(int clusterNum,int catalogIndex, Catalog catalog){
        //向一个目录项内写入目录信息
        byte[] catalogBytes = catalog.getBytes();
        System.arraycopy(catalogBytes, 0, contents, clusterNum * clusterCapacity + catalogIndex * catalogLength, 32);
    }

    //找到一个没有使用的簇。
    private int findEmptyClusterNumber(){
        //找到一个空的簇，返回他的簇号
        boolean loopOver = false;
        while (true){
            for(int i = 0; i< FATCapacity; i++){//FATLoopScanIndex < FATCapacity; FATLoopScanIndex++){
                int currClusterNum = findTheLinkedClusterNumber(i);//FATLoopScanIndex);
                if(currClusterNum == 0){
                    //若为0，则找到了，返回他就可以了
                    return i;//FATLoopScanIndex;
                }
                //若不为0，则还未找到，继续循环。
            }
            //若首次运行到这里，说明本次循环没有找到空的位置
            //应将FATLoopScanIndex置为0，重新寻找
            FATLoopScanIndex = 0;
            //若第二次运行到这里，说明已经满了
            if(loopOver){
                System.out.println("磁盘已满，无法写入。");
                return -1;
            }
            loopOver = true;
        }

    }

    //找到当前簇号在FAT表中链接的下一个簇号。
    private int findTheLinkedClusterNumber(int currClusterNumber){
        //根据当前的簇号，去FAT表中寻找他链接的下一个簇号，若到末尾了则返回-1，若当前簇未使用，则返回0
        int resClusterNum = 0;
        //因为每两个字节表示一个簇，所以要将簇号乘以2，才能获得FAT字节数组的正确对应索引。
        int FATIndex = currClusterNumber * 2;
        resClusterNum |= (FAT[FATIndex] & 0xFF) << 8;
        resClusterNum |= FAT[FATIndex+1] & 0xFF;
        if(resClusterNum == 0x0000FFFF){
            return -1;
        }
        else if(resClusterNum == 0x00000000){
            //若进入此块，说明该簇号
            //System.out.println("该簇号：" + currClusterNumber + "并未初始化，可以进行新簇的分配");
            return 0;
        }
        return resClusterNum;
    }

    //根据起始簇号，将文件的内容存入该簇，若该簇不足以存放此文件，则自动的链接新的簇，直到足以存下所有内容。
    private void saveFileContentToClusterAndAppendClustersIfNeeded(byte[] contents, int startClusterNum){
        assert contents != null;
        //这个函数用来向一个空的簇中拷贝文件的信息
        //如果这个簇满了，我们自动地开辟一个新簇，来存下面的信息
        int contentLength = contents.length;
        //至少需要多少个簇来保存这些信息。
        int clusterNumsNeedAtLeast = contentLength / clusterCapacity + 1;
        //这个数组存储每个簇号
        ArrayList<Integer> clusterNums = new ArrayList<>();
        clusterNums.add(startClusterNum);
        int prevClusterNum = startClusterNum;
        for(int i = 0; i < clusterNumsNeedAtLeast - 1; i++){
            //这个循环内做了链接所有簇的工作，即在FAT中做链接。
            int newClusterNum = findEmptyClusterNumber();
            //这里做了链接新的簇和旧簇的工作
            setFATItemBaseOnClusterNumAndInt(prevClusterNum, newClusterNum);
            setFATItemBaseOnClusterNumAndInt(newClusterNum, 0xFFFF);
            clusterNums.add(newClusterNum);
            //向前移动
            prevClusterNum = newClusterNum;
        }
        //contents的指针
        int pointerOfContents = 0;
        int i;
        for(i = 0; i < clusterNumsNeedAtLeast - 1; i++){
            //在最后一个簇之前，每个簇都是存4kB信息
            System.arraycopy(contents, pointerOfContents, this.contents, clusterNums.get(i) * clusterCapacity, clusterCapacity);
            pointerOfContents += clusterCapacity;
        }
        //到最后一个簇了，我们存剩余的信息进去
        System.arraycopy(contents, pointerOfContents, this.contents, clusterNums.get(i) * clusterCapacity, contentLength - pointerOfContents);
        //将最后一个簇的FAT链接置为-1
        setFATItemBaseOnClusterNumAndInt(clusterNums.get(i), 0xFFFF);
    }

    //根据路径，文件名，获得文件的内容。
    public byte[] getBytesOfCertainFile(String dirPath, String fileName) {
        ArrayList<Catalog> catalogs = getAllCatalogsFromDirPath(dirPath);
        if(catalogs ==  null){
            return null;
        }
        Catalog targetCatalog = null;
        for(Catalog catalog : catalogs){
            if(catalog.fileName.equals(fileName)){
                targetCatalog = catalog;
                break;
            }
        }
        if(targetCatalog == null){
            System.out.println("没有找到该文件！");
            return null;
        }
        //进入此处说明已经找到了文件
        int targetClusterNum = targetCatalog.clusterNum;
        int contentLength = targetCatalog.fileLengthOfBytes;
        byte[] res = new byte[contentLength];
        //根据簇号得到byte[]
        ArrayList<Integer> clusterNums = new ArrayList<>();
        clusterNums.add(targetClusterNum);
        int currClusterNum = findTheLinkedClusterNumber(targetClusterNum);
        while(currClusterNum != -1){
            clusterNums.add(currClusterNum);
            currClusterNum = findTheLinkedClusterNumber(currClusterNum);
        }
        for(int i = 0; i < clusterNums.size(); i++){
            if(i == clusterNums.size() - 1){
                //如果是最后一个簇，则复制剩余长度的byte
                //最后的复制长度为：文件总长度 - 已经复制了的长度
                //已经复制了的长度，为：(总簇号的数量-1)*簇长度
                System.arraycopy(this.contents, clusterNums.get(i) * clusterCapacity, res, i * clusterCapacity, contentLength - (clusterNums.size() - 1 ) * clusterCapacity);
            }else{
                //如果不是最后一个，复制4kB
                System.arraycopy(this.contents, clusterNums.get(i) * clusterCapacity, res, i * clusterCapacity, clusterCapacity);
            }
        }
        return res;
    }

    //根据一个含有"."以及".."的路径名，获得一个真正的路径名
    public static String getFullPathFromRelativePath(String pathContainsDots){
        //TODO : 根据一个含有点和点点的路径名，获取一个完整的路径。
        //此方法在调用前，应保证该路径是合法路径
        assert pathContainsDots.length() > 0;
        assert pathContainsDots.charAt(0) == '/';
        if(pathContainsDots.equals("/") || pathContainsDots.equals(".")){
            return "/";
        }
        String[] directories = pathContainsDots.split("/");
        directories = Arrays.copyOfRange(directories, 1, directories.length);
        ArrayList<String> resDirectories = new ArrayList<>();
        for(int i = 0; i < directories.length; i++){
            if(directories[i].equals(""))
                continue;
            if(directories[i].equals(".")){
                continue;
            }
            if(directories[i].equals("..")){
                if(resDirectories.size() > 0)
                    resDirectories.remove(resDirectories.size()-1);
                continue;
            }
            //进入此块，说明应该添加
            resDirectories.add(directories[i]);
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("/");
        for(String dir : resDirectories){
            stringBuilder.append(dir + "/");
        }

        return stringBuilder.toString();
    }

    //用于测试。
    public static void main(String[] args){
        System.out.println(FileSystem.getFullPathFromRelativePath("/abc"));
    }

}
