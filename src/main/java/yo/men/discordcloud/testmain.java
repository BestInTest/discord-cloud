package yo.men.discordcloud;

import yo.men.discordcloud.utils.FileHelper;

import java.io.IOException;

import static yo.men.discordcloud.utils.FileHelper.calculateMaxPartCount;

public class testmain {



    public static void main(String[] args) {
        String filePath = "2022-02-02-145744272.mp4";
        System.out.println(System.getProperty("user.dir"));
        int partNumber = 0; // Numer części pliku do wycięcia

        try {
            System.out.println(calculateMaxPartCount(filePath));
            System.out.println(FileHelper.getFilePart(filePath, partNumber).getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
