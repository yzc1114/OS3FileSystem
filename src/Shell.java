import java.util.ArrayList;
import java.util.Scanner;

public class Shell {

    public FileSystem fileSystem = null;

    public String cwd = "/";

    private String cmdTutorial =
            "指令帮助--------------------\n" +
            "指令类型 -> \n" +
            "ls 用来获取当前目录下的文件以及文件名.\n" +
            "cd [path] 用来进入以当前目录为相对路径的path目录.\n" +
            "mkdir [dirName] 用来在当前目录下生成一个名字为dirName的文件夹.\n" +
            "mkfile 用于在当前目录下新建一个文件，文件具体内容在之后输入.\n" +
            "rmdir 用于在当前目录下删除一个子目录.\n" +
            "rmfile 用于在当前目录下删除一个文件.\n" +
            "readfile [filename] 用于读取当前目录下的文件.\n" +
            "FORMAT 用于格式化硬盘.\n" +
            "SAVE 用于将该虚拟文件系统的内容存入真正的硬盘.\n" +
            "EXIT 用于退出系统，会自动保存虚拟文件系统的内容到硬盘上.\n";

    public void init(){
        fileSystem = new FileSystem();
        fileSystem.initSystem();
        fileSystem.loadFromDisk();
        String systemIntroduction = "本文件系统使用FAT表来进行空间管理。\n"
                + "文件系统总容量：" + fileSystem.hardDiskCapacity + "B\n"
                + "簇大小：" + fileSystem.clusterCapacity + "B\n"
                + "FAT表大小：" + fileSystem.FATCapacity + "B\n"
                + "目录项大小：" + fileSystem.catalogLength + "B\n";

        System.out.println(systemIntroduction);
        System.out.println(cmdTutorial);
        waitCMD();
    }

    private void waitCMD(){
        Scanner scanner = new Scanner(System.in);
        while(true){
            String commondPrompt = "当前目录：" + cwd + " >>> ";
            System.out.print(commondPrompt);
            String input = scanner.nextLine();
            if(input.length() == 0){
                System.out.println();
                continue;
            }
            else{
                String[] splitedCMD = input.split(" ");
                if(splitedCMD[0].equals("ls")){
                    executeLS();
                }else if(splitedCMD[0].equals("cd")){
                    if(splitedCMD.length < 2){
                        System.out.println("缺少参数.\n");
                        continue;
                    }
                    executeCD(splitedCMD[1]);
                }else if(splitedCMD[0].equals("mkdir")){
                    if(splitedCMD.length < 2){
                        System.out.println("缺少参数.\n");
                        continue;
                    }
                    executeMKDIR(splitedCMD[1]);
                }else if(splitedCMD[0].equals("mkfile")){
                    executeMKFILE();
                }else if(splitedCMD[0].equals("rmdir")){
                    if(splitedCMD.length < 2){
                        System.out.println("缺少参数.\n");
                        continue;
                    }
                    executeRMDIR(splitedCMD[1]);
                }else if(splitedCMD[0].equals("rmfile")){
                    if(splitedCMD.length < 2){
                        System.out.println("缺少参数.\n");
                        continue;
                    }
                    executeRMFILE(splitedCMD[1]);
                }
                else if(splitedCMD[0].equals("readfile")){
                    if(splitedCMD.length < 2){
                        System.out.println("缺少参数.\n");
                        continue;
                    }
                    executeREADFILE(splitedCMD[1]);
                }
                else if(splitedCMD[0].equals("FORMAT")){
                    executeFORMAT();
                }
                else if(splitedCMD[0].equals("SAVE")){
                    executeSAVE();
                }
                else if(splitedCMD[0].equals("EXIT")){
                    executeEXIT();
                }
                else{
                    System.out.println("指令不存在！");
                    System.out.println(cmdTutorial);
                }
            }

        }
    }

    private boolean executeFORMAT(){
        System.out.println("格式化开始！");
        fileSystem = new FileSystem();
        fileSystem.initSystem();
        cwd = "/";
        System.out.println("格式化完毕！");
        return true;
    }

    private boolean executeSAVE(){
        fileSystem.saveToDisk();
        return true;
    }

    private void executeEXIT(){
        executeSAVE();
        System.exit(0);
    }

    private boolean executeLS(){
        return fileSystem.listDir(cwd);
    }

    private boolean executeCD(String paraPath){
        String relativePath = cwd + paraPath;
        String fullPath = FileSystem.getFullPathFromRelativePath(relativePath);
        if(fileSystem.getClusterNumBaseOnDirectoryPath(fullPath) == -1){
            System.out.println("路径不存在！");
            return false;
        }
        cwd = fullPath;
        return true;
    }

    private boolean executeMKDIR(String dirName){
        if(dirName.equals("")){
            System.out.println("名称不能为空！");
            return false;
        }
        if(fileSystem.getClusterNumBaseOnDirectoryPath(cwd + dirName) != -1){
            System.out.println("文件夹已存在！");
            return false;
        }
        if(dirName.getBytes().length > 24){
            System.out.println("文件夹名长度过长！");
            return false;
        }
        if(fileSystem.addDir(cwd, dirName)){
            System.out.println("创建成功！");
            return true;
        }else {
            System.out.println("创建失败！");
            return false;
        }
    }

    private boolean executeMKFILE(){
        Scanner scanner = new Scanner(System.in);
        System.out.println("输入文件名，长度不能超过24个字符，按回车结束.");
        String fileName = scanner.nextLine();
        if(fileName.getBytes().length > 24){
            System.out.println("文件名不符合要求.");
            return false;
        }
        if(fileSystem.getClusterNumBaseOnDirectoryPath(cwd + fileName) != -1){
            System.out.println("文件名已存在！");
            return false;
        }
        System.out.println("输入文件内容，输入空行结束.");
        ArrayList<Byte> bytes = new ArrayList<>();
        //TODO 输入数据的方式需要解决。
        while(scanner.hasNextLine()){
            String line = scanner.nextLine();
            if(line.length() == 0)
                break;
            for(byte c : line.getBytes())
                bytes.add(c);
            bytes.add("\n".getBytes()[0]);

        }
        byte[] resBytes = new byte[bytes.size()];
        for(int i = 0; i < bytes.size(); i++){
            resBytes[i] = bytes.get(i);
        }
        if(fileSystem.addFile(cwd, fileName, resBytes)){
            System.out.println("创建成功！");
            return true;
        }else{
            System.out.println("创建失败！");
            return false;
        }
    }

    private boolean executeRMDIR(String dirName){
        if(!fileSystem.checkIfPathExists(cwd, dirName)){
            System.out.println("文件夹不存在！");
        }
        if(fileSystem.deleteSubDirectory(cwd, dirName)){
            System.out.println("删除成功！");
            return true;
        }else{
            System.out.println("删除失败！");
            return false;
        }
    }

    private boolean executeRMFILE(String fileName){
        if(!fileSystem.checkIfPathExists(cwd, fileName)){
            System.out.println("文件不存在！");
        }
        if(fileSystem.deleteFile(cwd, fileName)){
            System.out.println("删除成功！");
            return true;
        }else{
            System.out.println("删除失败！");
            return false;
        }
    }

    private boolean executeREADFILE(String fileName){
        byte[] content = fileSystem.getBytesOfCertainFile(cwd, fileName);
        if(content == null){
            System.out.println("文件读取失败！");
            return false;
        }
        System.out.println(new String(content));
        return false;
    }

    public static void main(String[] args){

    }
}
