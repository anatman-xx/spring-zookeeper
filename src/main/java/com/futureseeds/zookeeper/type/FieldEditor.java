package com.futureseeds.zookeeper.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import org.springframework.beans.FatalBeanException;
import org.springframework.context.ApplicationContext;

import com.futureseeds.zookeeper.ZkContext;

/**
 * Easy to use instance field editor
 * NOTE:will change the field accessible state
 */
public class FieldEditor {
	private Object object;
	private Field field;
	private Constructor<?> constructor;
	
	private ApplicationContext applicationContext;

	private SubscribeType subscribeType;
	private CreateStrategy createStrategy;

	public FieldEditor(Object object, Field field, SubscribeType subscribeType, CreateStrategy createStrategy,
			ApplicationContext applicationContext) {
		this.object = object;
		this.field = field;
		
		this.field.setAccessible(true);

		this.subscribeType = subscribeType;
		this.createStrategy = createStrategy;
		
		if (createStrategy == CreateStrategy.CONSTRUCTOR) {
			try {
				this.constructor = this.field.getType().getConstructor(String.class);
			} catch (SecurityException e) {
				throw new FatalBeanException("no suitable constructor found", e);
			} catch (NoSuchMethodException e) {
				throw new FatalBeanException("no suitable constructor found", e);
			}
		}
		
		this.applicationContext = applicationContext;
	}

	/**
	 * Get field value
	 */
	public Object get() {
		try {
			return field.get(object);
		} catch (IllegalArgumentException e) {
			throw new FatalBeanException("getting field value failed", e);
		} catch (IllegalAccessException e) {
			throw new FatalBeanException("getting field value failed", e);
		}
	}
	
	/**
	 * Set field with new value contructed by given string or bean
	 */
	public void set(String value) {
		ZkContext.trace("set field to " + value.toString());
		try {
			switch (createStrategy) {
			case CONSTRUCTOR:
				Object newValue = constructor.newInstance(value);
				field.set(object, newValue);

				break;

			case BEAN:
				Object bean = applicationContext.getBean(value);
				
				if (bean != null) {
					field.set(object, bean);
				}

				break;

			default:
				throw new FatalBeanException("Unsupported CreateStrategy");
			}

		} catch (IllegalArgumentException e) {
			throw new FatalBeanException("construct new instance failed", e);
		} catch (IllegalAccessException e) {
			throw new FatalBeanException("construct new instance failed", e);
		} catch (InstantiationException e) {
			throw new FatalBeanException("construct new instance failed", e);
		} catch (InvocationTargetException e) {
			throw new FatalBeanException("construct new instance failed", e);
		}
	}

	public SubscribeType getSubscribeType() {
		return subscribeType;
	}

	public CreateStrategy getCreateStrategy() {
		return createStrategy;
	}
}