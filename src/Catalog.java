import com.sun.nio.sctp.SctpStandardSocketOptions;

import java.util.ArrayList;
import java.util.List;

public class Catalog {

    //描述两种类型的数据：文件，子目录。
    public enum ItemType{
        file(), subDirectory
    }

    public String fileName; //文件名 （不支持长文件名）
    public ItemType itemType; //种类 （文件 or 子目录）
    public int fileLengthOfBytes; //文件长度
    public int clusterNum; //该目录所存的文件或文件夹的起始簇号
    public boolean valid; //该目录项是否有效

    public int catalogIndex; //该目录项在所在簇的目录项索引
    public int inWhichCluster; //该目录项所在簇的簇号

    //构造函数
    public Catalog(String fileName, ItemType itemType, int fileLengthOfBytes, int clusterNum, boolean valid){
        this.fileName = fileName;
        this.itemType = itemType;
        this.fileLengthOfBytes = fileLengthOfBytes;
        this.clusterNum = clusterNum;
        this.valid = valid;
    }

    //将目录项对象，转化为可以写入到文件系统的字节数组。
    public byte[] getBytes(){
        return Catalog.getCatalogBytesBaseOnAttributes(fileName, itemType, fileLengthOfBytes, clusterNum, valid);
    }

    //根据目录项所需的各种属性，获得一个目录项字节数组。
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

    //获得一个空的，无效的目录项。
    public static Catalog getInvalidCatalog(){
        return parseCatalogBytes(new byte[32]);
    }

    public static void main(String[] args){
        byte[] bytes = getCatalogBytesBaseOnAttributes("abc", ItemType.file, 12, 2, true);
        if(bytes == null){
            return;
        }
        for(int i = 0; i < 4; i++){
            System.out.println(Integer.toHexString(bytes[i]));
        }
        Catalog c = parseCatalogBytes(bytes);
        System.out.println(c.fileName);
        System.out.println(c.itemType);
        System.out.println(c.fileLengthOfBytes);
        System.out.println(c.clusterNum);
        System.out.println(c.valid);
    }
}
