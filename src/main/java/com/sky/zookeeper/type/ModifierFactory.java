package com.sky.zookeeper.type;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.springframework.context.ApplicationContext;

public class ModifierFactory {
	private static ModifierFactory instance = null;

	private ApplicationContext applicationContext;

	private ModifierFactory(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public FieldEditor getFieldEditor(Object object, Field field, SubscribeType subscribeType, CreateStrategy createStrategy) {
		return new FieldEditor(object, field, applicationContext, subscribeType, createStrategy);
	}
	
	public MethodInvoker getMethodInvoker(Object object, Method method, SubscribeType subscribeType, CreateStrategy createStrategy) {
		return new MethodInvoker(object, method, applicationContext, subscribeType, createStrategy);
	}
	
	public Modifier getModifier(Object object, AccessibleObject member, SubscribeType subscribeType, CreateStrategy createStrategy) {
		if (member instanceof Field) {
			return getFieldEditor(object, (Field) member, subscribeType, createStrategy);
		} else if (member instanceof Method) {
			return getMethodInvoker(object, (Method) member, subscribeType, createStrategy);
		}

		return null;
	}
	
	public static ModifierFactory getInstance(ApplicationContext applicationContext) {
		if (instance != null) {
			return instance;
		}
		
		instance = new ModifierFactory(applicationContext);

		return instance;
	}
}
