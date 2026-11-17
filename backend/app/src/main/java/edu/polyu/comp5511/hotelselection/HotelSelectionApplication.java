package edu.polyu.comp5511.hotelselection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HotelSelectionApplication {
    public static void main(String[] args) {
        SpringApplication.run(HotelSelectionApplication.class, args);
    }
}
