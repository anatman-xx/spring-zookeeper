package com.sky.zookeeper.type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MethodInvoker {
	private static final Logger LOGGER = LoggerFactory.getLogger(MethodInvoker.class);

	private Object object;
	private Method method;

	public MethodInvoker(Object object, Method method) {
		this.object = object;
		this.method = method;
	}
	
	public void invoke(String arg) {
		LOGGER.trace("invoke method with argument " + arg);
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
