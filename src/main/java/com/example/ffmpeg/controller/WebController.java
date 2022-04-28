package com.example.ffmpeg.controller;


import com.example.ffmpeg.service.WebService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
@Slf4j
public class WebController {

    @Autowired
    WebService webService;

    @RequestMapping("/codes/")
    public String inputCode(@RequestParam("codes") String codes){
        System.out.println("code" + codes);
        try {
          String[] result = codes.split(",");
            for (String s : result) {
                webService.exe(s);
            }
        } catch (Exception e) {
            log.error(e);
            return "error";
        }
        return "ok";
    }
}
