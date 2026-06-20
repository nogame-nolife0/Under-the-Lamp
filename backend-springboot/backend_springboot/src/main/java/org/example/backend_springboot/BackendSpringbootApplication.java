package org.example.backend_springboot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.example.backend_springboot.mapper")
public class BackendSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendSpringbootApplication.class, args);
    }

}
