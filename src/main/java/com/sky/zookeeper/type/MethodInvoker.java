package com.sky.zookeeper.type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.springframework.context.ApplicationContext;


public class MethodInvoker extends Modifier {
	private Method method;

	public MethodInvoker(Object object, Method method, ApplicationContext applicationContext,
			SubscribeType subscribeType, CreateStrategy createStrategy) {
		this.object = object;
		this.method = method;

		setSubscribeType(subscribeType);
		setCreateStrategy(createStrategy);
	}
	
	/**
	 * not yet support BEAN create strategy
	 */
	public void invoke(Object arg) {
		LOGGER.debug("invoke method with argument " + arg);

		try {
			method.invoke(object, arg);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void eval(Object arg) {
		invoke(arg);
	}
}
