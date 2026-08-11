package ar.edu.utn.dds.k3003.app;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableFeignClients(basePackages = "ar.edu.utn.dds.k3003.clients")
@ComponentScan(basePackages = "ar.edu.utn.dds.k3003")
@EnableJpaRepositories(basePackages = "ar.edu.utn.dds.k3003.repositories")
@EntityScan(basePackages = "ar.edu.utn.dds.k3003.model")
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
