package demo;

import auto.AutoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j

@RegisterReflectionForBinding({
		// points
		Points.class, Points.Geometry.class, Points.PointsProperties.class,
		// forecast
		Forecast.class, Forecast.ForecastProperties.Period.class, Forecast.ForecastProperties.class })

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	WebClient webClient(WebClient.Builder builder) {
		var size = 262144 * 10;
		var strategies = ExchangeStrategies.builder()
			.codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
			.build();
		return builder.exchangeStrategies(strategies).build();
	}

	@Bean
	ApplicationRunner applicationRunner(Todos todos, Weather weather, Newspapers newspapers) {
		return arguments -> {
			var map = Map.<String, Supplier<Object>>of(//
					"newspapers", () -> newspapers.newspapers().get("newspapers"), //
					"todos", () -> todos.todoById(192), //
					"weather forecast", () -> {
						var points = weather.points(37.7897d, -122.4009d);
						return weather.forecast(points.properties().gridId(), //
								points.properties().gridX(), //
								points.properties().gridY()//
						);
					});
			map.forEach((name, supplier) -> {
				var result = supplier.get();
				log.info("================================");
				log.info(name.toUpperCase(Locale.ENGLISH));
				log.info(result.toString());
			});
		};
	}

}

@AutoClient
@HttpExchange("https://jsonplaceholder.typicode.com")
interface Todos {

	@GetExchange("/todos/{id}")
	Todo todoById(@PathVariable int id);

}

@AutoClient
@HttpExchange("https://chroniclingamerica.loc.gov")
interface Newspapers {

	@GetExchange("/newspapers.json")
	Map<String, List<Newspaper>> newspapers();

}

@AutoClient
@HttpExchange("https://api.weather.gov")
interface Weather {

	@GetExchange("/points/{latitude},{longitude}")
	Points points(@PathVariable double latitude, @PathVariable double longitude);

	@GetExchange("/gridpoints/{office}/{gridX},{gridY}/forecast")
	Forecast forecast(@PathVariable String office, @PathVariable int gridX, @PathVariable int gridY);

}

record Forecast(ForecastProperties properties) {
	record ForecastProperties(Period[] periods) {
		record Period(int number, String name, String startTime, String endTime, boolean isDaytime, float temperature,
				String temperatureUnit) {
		}
	}
}

record Points(PointsProperties properties, Geometry geometry) {

	record PointsProperties(String gridId, int gridX, int gridY) {
	}

	record Geometry(String type, double[] coordinates) {
	}

}

record Newspaper(String lccn, String state, URL url, String title) {
}

record Todo(int userId, int id, String title, boolean completed) {
}
