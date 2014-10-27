package com.sky.zookeeper.type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.sky.zookeeper.ZkContext;

public class MethodInvoker {
	private Object object;
	private Method method;

	public MethodInvoker(Object object, Method method) {
		this.object = object;
		this.method = method;
	}
	
	public void invoke(String arg) {
		ZkContext.LOGGER.trace("invoke method with argument " + arg);
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
