package com.guidesound.controller;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

@Controller
@RequestMapping("/")
public class FileController
{
    class Reps {
        int code;
        String msg;
        Object data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    static String articleBucketName;
    static String videoBucketName;
    static String region;
    static COSClient cosClient = null;
    static {
        articleBucketName = "pic-article-1257964795";
        videoBucketName = "video-1257964795";
        region = "ap-beijing";
        COSCredentials cred = new BasicCOSCredentials("AKIDkIbfU4YZXUDgttF7MPDl36vUw9E6o7GK", "zjHchX8UbSCj9MM7ORFo8uUpwoUw9ltq");
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        cosClient = new COSClient(cred, clientConfig);
    }

    @RequestMapping("/upload")
    @ResponseBody
    public Object upLoad(HttpServletRequest request) throws IOException {
        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        String sign = multipartRequest.getParameter("sign");
        Reps reps = new Reps();
        if (sign == null || !sign.equals("guide_sound")) {
            reps.code = 201;
            reps.msg = "缺少sign";
            return reps;
        }

        MultipartFile fileUpload = multipartRequest.getFile("upload");
        if(fileUpload == null) {
            reps.code = 201;
            reps.msg = "缺少上传文件";
            return reps;
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss_");//设置日期格式
        String strDate = df.format(new Date());// new Date()为获取当前系统时间

        String savePath = multipartRequest.getServletContext().getRealPath("");
        System.out.println(savePath);
        File file = new File(savePath);
        savePath = file.getParent() + "/file";
        File filePath = new File(savePath);
        if (!filePath.exists() && !filePath.isDirectory()) {
            filePath.mkdir();
        }
        String fileName = fileUpload.getOriginalFilename();
        String[] strs = fileName.split("\\.");
        String randStr = getRandomString(8);
        String suffix = "";
        if (strs.length > 1) {
            suffix = strs[strs.length -1];
        }

        String pathFile = savePath + "/" + strDate + randStr + "." + suffix;
        File localFile = new File(pathFile);
        fileUpload.transferTo(localFile);

        String key = strDate + randStr + "." + suffix;
        PutObjectRequest putObjectRequest = new PutObjectRequest(articleBucketName, key, localFile);
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        localFile.delete();
        String url = "https://" + articleBucketName + ".cos." + region + ".myqcloud.com" + "/" + key;
        reps.code = 200;
        reps.msg = "ok";
        reps.data = url;
        return reps;
    }

    @RequestMapping("/convert_video")
    @ResponseBody
    Object convertVideo(HttpServletRequest request) {
        String sign = request.getParameter("sign");
        String url = request.getParameter("convert_url");

        Reps reps = new Reps();
        if (sign == null || !sign.equals("guide_sound") || url == null) {
            reps.code = 201;
            reps.msg = "缺少参数";
            return reps;
        }

        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String tmpPath = req.getServletContext().getRealPath("")
                + "/tmp/";
        File filePath = new File(tmpPath);
        if (!filePath.exists() && !filePath.isDirectory()) {
            filePath.mkdir();
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss_");//设置日期格式
        String strDate = df.format(new Date());// new Date()为获取当前系统时间

        String fileName = strDate + getRandomString(8) + ".mp4";
        String savaPath = tmpPath + fileName;
        videoChange(url,savaPath);

        File fTemp = new File(savaPath);
        if(!fTemp.exists()) {
            reps.code = 500;
            reps.msg = "文件不存在";
            reps.data = savaPath;
            return reps;
        }

        String key = fileName;
        PutObjectRequest putObjectRequest = new PutObjectRequest(videoBucketName, key, fTemp);
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        fTemp.delete();

        String ret_url = "https://" + videoBucketName + ".cos." + region + ".myqcloud.com" + "/" + key;
        reps.code = 200;
        reps.msg = "ok";
        reps.data = ret_url;
        return reps;
    }


    /**
     *生成随机字符串
     */
    public static String getRandomString(int length){
        String str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random=new Random();
        StringBuffer sb=new StringBuffer();
        for(int i=0;i<length;i++){
            int number=random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    /**
     *视频转码
     */
    public static boolean videoChange(String from,String to) {

        try {
            exec("/home/ubuntu/wu/dyFFmpeg "
                    + from + " " + to);
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    //执行命令
    public static String exec(String command) throws InterruptedException {
        String returnString = "";
        Process pro = null;
        Runtime runTime = Runtime.getRuntime();
        if (runTime == null) {
            System.err.println("Create runtime false!");
        }
        try {
            pro = runTime.exec(command);
            BufferedReader input = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            PrintWriter output = new PrintWriter(new OutputStreamWriter(pro.getOutputStream()));
            String line;
            while ((line = input.readLine()) != null) {
                returnString = returnString + line + "\n";
            }
            input.close();
            output.close();
            pro.destroy();
        } catch (IOException ex) {
            System.out.println(ex);
        }
        return returnString;
    }

}
