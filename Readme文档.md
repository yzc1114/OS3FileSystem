# 操作系统作业 文件管理系统

### 开发语言
使用IntelliJ IDEA IDE开发
语言：java SE 12

### 项目目录
1. src文件夹中包含源代码
- Main.java：启动程序。
- Catalog.java：定义目录项类。
- FileSystem.java：定义文件系统类，核心代码。
- Shell.java：定义界面Shell，实现与用户的交互。

2. out文件夹中包含编译完成的class文件。

3. os3.jar为打包好的可执行文件，使用方法见下。

### 使用说明
在机器上安装好java（jre）的情况下，在根目录使用命令行：
```
java -jar os3.jar
```
即可使用该程序。使用方法类似于linux，Unix的shell。

本程序有十个指令。
指令类型:
- ls 用来获取当前目录下的文件以及文件名.
- cd [path] 用来进入以当前目录为相对路径的path目录.
- mkdir [dirName] 用来在当前目录下生成一个名字为dirName的文件夹.
- mkfile 用于在当前目录下新建一个文件，文件具体内容在之后输入.
- rmdir 用于在当前目录下删除一个子目录.
- rmfile 用于在当前目录下删除一个文件.
- readfile [filename] 用于读取当前目录下的文件.
- FORMAT 用于格式化硬盘.
- SAVE 用于将该虚拟文件系统的内容存入真正的硬盘.
- EXIT 用于退出系统，会自动保存虚拟文件系统的内容到硬盘上.

### 实现界面
使用类似linux, Unix的Shell界面。
![shell界面](/img/1.png)

### 实现准则
本文件管理系统使用“位图”与“FAT显式链接”结合起来的方式实现的该文件系统。

### 局限性
本文件系统仅支持ascii码的写入，并且文件名和文件夹名的长度最大为24个ascii码。

### 文件系统配置
- 文件系统总容量：256MB
- 文件系统簇大小：4KB
- 每个簇在FAT表中所占大小：2B
- FAT表的总大小：128KB
- 单个目录项的大小：32B

#### 目录项结构说明
- 字节1：代表该目录项是否有效
- 字节2-25：存储该文件或文件名的名称
- 字节26：存储该目录项的类型（文件/文件夹）
- 字节27-30：存储该文件的长度（为文件夹时，该四字节无效）
- 字节31-32：存储该文件/文件夹的起始簇号

**规定未使用的簇在FAT表中内容存储为0x0000**
**终止簇号在FAT表中内容表示为0xFFFF**

### 核心算法及代码
1. 获得一个文件夹包含的所有目录项（十分重要）
```
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
```

2. 找到给定簇链接的下一个簇
```
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
```

3. 创建文件或文件夹
```
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
```
4. 删除子目录
```
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
```

5. 删除文件
```
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
```

6. 读取文件
```
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
```

7. 根据目录项的各种属性，获取一个目录项字节数组信息。
```
//根据目录项所需的各种属性，获得一个目录项对象。
    public static byte[] getCatalogBytesBaseOnAttributes(String fileName, ItemType itemType, int fileLengthOfBytes, int clusterNum, boolean valid){
        //一共用32个byte表示一个目录项
        //最后2个byte表示起始簇号 最大簇号为 2^16 而一共有 2^15个簇 所以足够存放
        //往前4个byte表示文件长度 文件最大长度为2^32 Byte 即 4G 完全足够
        //往前1个byte表示类型 只有两种类型：文件和子目录 足够区分
        //往前剩余的24个byte表示文件名，最多为24个ascii码
        //剩下的一个byte表示该目录项是否有效

        //需要检测输入的数据是否合法
        //检测字符串是否全是ascii码
        if(fileName.getBytes().length != fileName.length()){
            return null;
        }
        //检测字符串的长度是否小于等于24
        if(fileName.length() > 24){
            return null;
        }
        //检验通过，开始赋值
        //存储结果
        byte[] res = new byte[32];
        //将有效位置入第一个字节
        res[0] = valid ? (byte)0x01 : (byte)0x00;
        //获得文件名的字节数组
        byte[] fileNameBytes = fileName.getBytes();
        //指针扫描一遍字节数组
        int pointer = 1;
        //给文件名赋值
        for(; pointer < fileNameBytes.length + 1; pointer++){
            res[pointer] = fileNameBytes[pointer - 1];
        }
        //给文件名剩余部分赋0
        for(; pointer < 25; pointer++){
            res[pointer] = 0x00;
        }
        //给类型赋值 0表示文件 1表示文件夹
        res[pointer++] = itemType.equals(ItemType.file) ? (byte)0x00 : (byte)0x01;
        //给文件长度赋值
        byte[] bytesArrayOfFileLengthOfBytes = new byte[4];
        bytesArrayOfFileLengthOfBytes[3] = (byte) ((fileLengthOfBytes>>24) & 0xFF);
        bytesArrayOfFileLengthOfBytes[2] = (byte) ((fileLengthOfBytes>>16) & 0xFF);
        bytesArrayOfFileLengthOfBytes[1] = (byte) ((fileLengthOfBytes>>8) & 0xFF);
        bytesArrayOfFileLengthOfBytes[0] = (byte) ((fileLengthOfBytes) & 0xFF);
        for(; pointer < 30; pointer++){
            res[pointer] = bytesArrayOfFileLengthOfBytes[29 - pointer];
        }
        //给簇号赋值
        byte[] bytesArrayOfClusterNum = new byte[2];
        bytesArrayOfClusterNum[1] = (byte)(clusterNum>>8);
        bytesArrayOfClusterNum[0] = (byte)(clusterNum);
        for(; pointer < 32; pointer++){
            res[pointer] = bytesArrayOfClusterNum[31 - pointer];
        }
        return res;
    }

```

8. 根据目录项的字节数组，解析成为一个目录项对象。
```
//根据包含目录项的字节数组，解析成一个目录项对象。
    public static Catalog parseCatalogBytes(byte[] bytes){
        //将字节数组catalog目录项解析成自己的目录项对象
        //太有用了这个函数
        //牛逼
        if(bytes.length != 32){
            return null;
        }
        ArrayList<Byte> fileNameBytesArrayList = new ArrayList<>();
        int pointer = 0;
        boolean valid = bytes[pointer++] == 0x01;
        for(;pointer < 25; pointer++){
            if(bytes[pointer] == 0x00){
                pointer = 25;
                break;
            }
            fileNameBytesArrayList.add(bytes[pointer]);
        }
        byte[] fileNameBytes = new byte[fileNameBytesArrayList.size()];
        for(int i = 0; i < fileNameBytes.length; i++){
            fileNameBytes[i] = (fileNameBytesArrayList.get(i));
        }
        String fileName = new String(fileNameBytes);
        ItemType type = bytes[pointer++] == (byte)0x00 ? ItemType.file: ItemType.subDirectory;
        int fileLengthOfBytes = 0;
        fileLengthOfBytes |= ((bytes[pointer++]&0xFF) << 24);
        fileLengthOfBytes |= ((bytes[pointer++]&0xFF) << 16);
        fileLengthOfBytes |= ((bytes[pointer++]&0xFF) << 8);
        fileLengthOfBytes |= ((bytes[pointer++])&0xFF);
        int clusterNum = 0;
        clusterNum |= bytes[pointer++] << 8;
        clusterNum |= bytes[pointer];
        return new Catalog(fileName, type, fileLengthOfBytes, clusterNum, valid);
    }
```

