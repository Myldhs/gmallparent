package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.io.FilenameUtils;
import org.csource.common.MyException;
import org.csource.fastdfs.ClientGlobal;
import org.csource.fastdfs.StorageClient1;
import org.csource.fastdfs.TrackerClient;
import org.csource.fastdfs.TrackerServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @author ldh
 * @create 2020-04-18 15:29
 */
@Api(tags = "文件上传管理")
@RestController // 相当于@ResponseBody + @Controller
@RequestMapping("/admin/product")
public class FileUploadController {


    //获取配置文件中配置的文件服务器的URL 用于拼接保存的文件URL
    @Value("${fileServer.url}")
    private String fileUrl;

    // 文件上传完成之后返回文件地址：
    // http://img12.360buyimg.com/n7/jfs/t1/97129/16/18957/134723/5e9977a9Ea874f1c3/d8df81e4e62d8de3.jpg
    // 配置文件服务的Ip地址,放在配置文件中，实现了软编码。
    @ApiOperation(value = "文件上传")
    @PostMapping("fileUpload")// springMVC 自动封装了一个文件上传的类。 file 是前端页面指定好的。
    public Result fileUpload(MultipartFile file) throws IOException, MyException {

        // 获取resource 目录下的tracker.conf配置文件的配置信息 注意：项目目录中千万不能有中文！
        String configFile = this.getClass().getResource("/tracker.conf").getFile();

        //创建图片返回路径变量
        String impPath = null;

        //判断上传的文件是否为空
        if(file!=null){
            //初始化上传文件  参数为上传的文件的配置信息
            ClientGlobal.init(configFile);

            //创建访问trackerServer的客户端 trackerClient
            TrackerClient trackerClient = new TrackerClient();

            //由trackerClient 获取trackerServer
            TrackerServer trackerServer = trackerClient.getConnection();

            //创建storageClient 用于真正上传文件
            StorageClient1 storageClient1 = new StorageClient1(trackerServer, null);

            // 上传文件
            // 第一个参数表示要上传文件的字节数组
            // 第二个参数：文件的后缀名
            // 第三个参数： 数组，null
            //返回值是保存在文件服务器中的文件名 拼接上fileUrl就是上传文件的完整URL
            impPath = storageClient1.upload_appender_file1(file.getBytes(), FilenameUtils.getExtension(file.getOriginalFilename()), null);

            // 上传完成之后，需要获取到文件的上传路径
            System.out.println("图片路径："+fileUrl + impPath);
        }

        //返回图片上传的完整URL路径
        return Result.ok(fileUrl+impPath);
    }
}
