package auto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.web.service.annotation.HttpExchange;

@Slf4j
class HttpClientClientDetectionStrategy implements ClientDetectionStrategy {

	private final MergedAnnotations.Search mergedAnnotations = MergedAnnotations
		.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);

	@Override
	public boolean test(Class<?> aClass) {
		return this.mergedAnnotations.from(aClass).get(AutoClient.class).isPresent()
				&& this.mergedAnnotations.from(aClass).get(HttpExchange.class).isPresent();
	}

}
