package com.hightraffic.ecommerce.loadtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.IOException;

@SpringBootApplication
public class RunMockServer {
    public static void main(String[] args) throws IOException {
        MockInventoryServer.main(args);
    }
}