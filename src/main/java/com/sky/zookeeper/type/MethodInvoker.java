package com.sky.zookeeper.type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.springframework.context.ApplicationContext;

import com.sky.zookeeper.annotation.ZkValue;


public class MethodInvoker extends Modifier {
	private Method method;

	public MethodInvoker(Object object, Method method, ApplicationContext applicationContext) {
		this.object = object;
		this.method = method;

		ZkValue annotation = method.getAnnotation(ZkValue.class);

		setSubscribeType(annotation.subscribeType());
		setCreateStrategy(annotation.createStrategy());
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
}
