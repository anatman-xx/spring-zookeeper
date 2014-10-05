package com.sky.zookeeper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;
import org.springframework.util.ReflectionUtils.FieldFilter;

import com.github.zkclient.ZkClient;
import com.sky.zookeeper.annotation.ZkLeader;
import com.sky.zookeeper.annotation.ZkManage;
import com.sky.zookeeper.annotation.ZkValue;
import com.sky.zookeeper.handler.ZkDataChangeEventHandler;
import com.sky.zookeeper.type.CreateStrategy;
import com.sky.zookeeper.type.FieldEditor;
import com.sky.zookeeper.type.SubscribeType;

@Component
public abstract class ZkContext implements InitializingBean, ApplicationContextAware {
	public static final Logger LOGGER = LoggerFactory.getLogger(ZkContext.class);

	public static final FieldFilter ZKVALUE_ANNOTATED_FIELDS = new FieldFilter() {
		@Override
		public boolean matches(Field field) {
			return ReflectionUtils.COPYABLE_FIELDS.matches(field) && field.isAnnotationPresent(ZkValue.class);
		}
	};

	public static final FieldFilter ZKLEADER_ANNOTATED_FIELDS = new FieldFilter() {
		@Override
		public boolean matches(Field field) {
			return ReflectionUtils.COPYABLE_FIELDS.matches(field) && field.isAnnotationPresent(ZkLeader.class)
					&& field.getDeclaringClass().equals(Boolean.class);
		}
	};

	private ApplicationContext applicationContext;

	private ZkClient zkClient;
	private Map<String, Set<FieldEditor>> zkPathMapping;

	public String getZkConnection() {
		return "localhost:2181";
	}

	public ZkClient getZkClient() {
		return zkClient;
	}

	public Map<String, Set<FieldEditor>> getZkPathMapping() {
		return zkPathMapping;
	}

	/**
	 * Scan for Zk* annotated field in which beans annotated with ZkManage
	 */
	private void scanFields() {
		for (final Object bean : applicationContext.getBeansWithAnnotation(ZkManage.class).values()) {
			LOGGER.trace("found bean(" + bean.getClass().getName() + ") with ZkManage");

			ReflectionUtils.doWithFields(bean.getClass(), new FieldCallback() {
				@Override
				public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found field(" + field.getName() + ") with ZkValue");

					registerZkValue(bean, field, true);
				}
			}, ZKVALUE_ANNOTATED_FIELDS);
			
			ReflectionUtils.doWithFields(bean.getClass(), new FieldCallback() {
				@Override
				public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found field(" + field.getName() + ") with ZkLeader");

					registerZkLeader(bean, field, true);
				}
			}, ZKLEADER_ANNOTATED_FIELDS);
		}
	}

	private boolean validateZkPathMapping() {
		Map<String, SubscribeType> zkPathSubscribeTypeMapping = new HashMap<String, SubscribeType>();
		Map<String, CreateStrategy> zkPathCreateStrategyMapping = new HashMap<String, CreateStrategy>();

		for (Entry<String, Set<FieldEditor>> entry : zkPathMapping.entrySet()) {
			for (FieldEditor fieldEditor : entry.getValue()) {
				if (zkPathSubscribeTypeMapping.containsKey(entry.getKey())
						&& zkPathSubscribeTypeMapping.get(entry.getKey()) != fieldEditor.getSubscribeType()) {
					throw new FatalBeanException("Confilct SubscribeType");
				}

				if (zkPathCreateStrategyMapping.containsKey(entry.getKey())
						&& zkPathCreateStrategyMapping.get(entry.getKey()) != fieldEditor.getCreateStrategy()) {
					throw new FatalBeanException("Confilct CreateStrategy");
				}
			}
		}

		return true;
	}

	/**
	 * NOTE:You should make sure that all FieldEditor in the set with the same
	 * SubscribeType and the same CreateStrategy
	 */
	private boolean registerZkEvent(String zkPath, Set<FieldEditor> fieldEditorSet) {
		FieldEditor fieldEditor = (FieldEditor) fieldEditorSet.toArray()[0];

		switch (fieldEditor.getSubscribeType()) {
		case DATA_CHANGE:
			zkClient.subscribeDataChanges(zkPath, new ZkDataChangeEventHandler(fieldEditorSet));

			break;

		default:
			break;
		}

		return true;
	}
	
	private boolean registerZkLeader(Object bean, Field field, boolean initial) {
		return true;
	}

	private boolean registerZkValue(Object bean, Field field, boolean initial) {
		ZkValue annotation = field.getAnnotation(ZkValue.class);
		String zkPath = annotation.value();

		FieldEditor fieldEditor = new FieldEditor(bean, field, annotation.subscribeType(), annotation.createStrategy(),
				applicationContext);

		if (initial) {
			byte[] dataByte = zkClient.readData(zkPath, true);

			if (dataByte == null) {
				throw new FatalBeanException("no data found on path \"" + zkPath + "\"");
			}

			String data = new String(dataByte);

			LOGGER.trace("read data(" + data + ") on ZkPath(" + zkPath + ")");
			fieldEditor.set(data);
		}

		if (zkPathMapping.containsKey(zkPath)) {
			zkPathMapping.get(zkPath).add(fieldEditor);
		} else {
			Set<FieldEditor> fieldEditorSet = new HashSet<FieldEditor>();
			fieldEditorSet.add(fieldEditor);

			zkPathMapping.put(zkPath, fieldEditorSet);
		}

		return true;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		scanFields();

		if (validateZkPathMapping()) {
			for (Entry<String, Set<FieldEditor>> entry : zkPathMapping.entrySet()) {
				registerZkEvent(entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		this.zkClient = new ZkClient(getZkConnection(), 1000);
	}
}
