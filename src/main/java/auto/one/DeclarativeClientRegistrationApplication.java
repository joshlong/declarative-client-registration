package auto.one;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;


@SpringBootApplication
public class DeclarativeClientRegistrationApplication {

/*    public static void main(String[] args) {
        SpringApplication.run(DeclarativeClientRegistrationApplication.class, args);
    }*/

    @Bean
    WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ApplicationRunner runner(Todos todos) {
        return args -> todos.todos().forEach(System.out::println);
    }

}


@HttpExchange("https://jsonplaceholder.typicode.com")
interface Todos {

    @GetExchange("/todos")
    List<Todo> todos();

    @GetExchange("/todos/{id}")
    List<Todo> todoById(@PathVariable int id);
}

record Todo(int userId, int id, String title, boolean completed) {
}
