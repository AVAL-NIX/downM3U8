package com.example.ffmpeg.controller;


import com.example.ffmpeg.hander.DownlandTask;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class WebController {

    @RequestMapping("/codes/")
    public String inputCode(@RequestParam("codes") String codes){
        System.out.println("code" + codes);
        try {
          String[] result = codes.split(",");
            for (String s : result) {
                DownlandTask.WAIT_QUEUE.put(s.trim());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return "error";
        }
        return "ok";
    }
}
