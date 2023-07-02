package auto.one;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.Map;

class DeclarativeClientFactoryBean<T> implements BeanFactoryAware, FactoryBean<T> {

	private Class<?> type;

	private ListableBeanFactory context;

	private final Logger log = LoggerFactory.getLogger(getClass());

	private WebClient findWebClient(Class<?> clazz, ListableBeanFactory beanFactory) {
		Assert.notNull(clazz, "the class must be non-null");
		Assert.notNull(beanFactory, "the beanFactory must be non-null");

		Map<String, WebClient> webClientMap = beanFactory.getBeansOfType(WebClient.class);
		Assert.state(webClientMap.size() >= 1, "there should be at least one WebClient!");

		MergedAnnotation<Qualifier> qualifierMergedAnnotation = MergedAnnotations.from(clazz).get(Qualifier.class);
		if (qualifierMergedAnnotation.isPresent()) {
			String valueOfQualifier = qualifierMergedAnnotation.getString(MergedAnnotation.VALUE);
			WebClient wc = BeanFactoryAnnotationUtils.qualifiedBeanOfType(beanFactory, WebClient.class,
					valueOfQualifier);
			log.debug("found one _qualified_ WebClient");
			return wc;
		}

		log.debug("looking for the valid WebClient for interface " + clazz.getName() + '.');
		if (webClientMap.size() == 1) {
			log.debug("only found one WebClient in the context; returning.");
			return webClientMap.values().iterator().next();
		}

		throw new IllegalStateException(
				"there should be one and only one WebClient instance or one and only one qualified WebClient instance!");
	}

	/*
	 * private static RSocketRequester forInterface(Class<?> clientInterface,
	 * ListableBeanFactory context) { Map<String, RSocketRequester>
	 * rSocketRequestersInContext = context.getBeansOfType(RSocketRequester.class); int
	 * rSocketRequestersCount = rSocketRequestersInContext.size();
	 * Assert.state(rSocketRequestersCount > 0, () -> "there should be at least one " +
	 * RSocketRequester.class.getName() +
	 * " in the context. Please consider defining one."); RSocketRequester
	 * rSocketRequester = null; Assert.notNull(clientInterface,
	 * "the client interface must be non-null"); Assert.notNull(context, () -> "the " +
	 * ListableBeanFactory.class.getName() + " interface must be non-null");
	 * MergedAnnotation<Qualifier> qualifier =
	 * MergedAnnotations.from(clientInterface).get(Qualifier.class); if
	 * (qualifier.isPresent()) { String valueOfQualifierAnnotation =
	 * qualifier.getString(MergedAnnotation.VALUE); Map<String, RSocketRequester> beans =
	 * BeanFactoryAnnotationUtils.qualifiedBeansOfType(context, RSocketRequester.class,
	 * valueOfQualifierAnnotation); Assert.state(beans.size() == 1, () ->
	 * "I need just one " + RSocketRequester.class.getName() + " but I got " +
	 * beans.keySet()); for (Map.Entry<String, RSocketRequester> entry : beans.entrySet())
	 * { rSocketRequester = entry.getValue(); if (log.isDebugEnabled()) {
	 * log.debug("found " + rSocketRequester + " with bean name " + entry.getKey() +
	 * " for @" + RSocketClient.class.getName() + " interface " +
	 * clientInterface.getName() + '.'); } } } else { Assert.state(rSocketRequestersCount
	 * == 1, () -> "there should be no more and no less than one unqualified " +
	 * RSocketRequester.class.getName() + " instances in the context."); return
	 * rSocketRequestersInContext.values().iterator().next(); }
	 * Assert.notNull(rSocketRequester, () -> "we could not find an " +
	 * RSocketRequester.class.getName() + " for the @RSocketClient interface " +
	 * clientInterface.getName() + '.'); return rSocketRequester; }
	 */

	public void setType(String type) {
		try {
			this.type = Class.forName(type);
		} //
		catch (Throwable throwable) {
			System.err.println("oops!" + throwable.getMessage());
		}
	}

	@Override
	public T getObject() {
		WebClient wc = this.findWebClient(this.type, this.context);
		return (T) HttpServiceProxyFactory.builder(WebClientAdapter.forClient(wc)).build().createClient(this.type);
	}

	@Override
	public Class<?> getObjectType() {
		return this.type;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		Assert.state(beanFactory instanceof ListableBeanFactory, () -> "the " + BeanFactory.class.getName()
				+ " is not an instance of a " + ListableBeanFactory.class.getName());
		this.context = (ListableBeanFactory) beanFactory;
	}

}
