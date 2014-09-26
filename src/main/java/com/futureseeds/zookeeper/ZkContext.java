package com.futureseeds.zookeeper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import com.futureseeds.zookeeper.annotation.ZkManage;
import com.futureseeds.zookeeper.annotation.ZkValue;
import com.futureseeds.zookeeper.handler.ZkDataChangeEventHandler;
import com.futureseeds.zookeeper.type.CreateStrategy;
import com.futureseeds.zookeeper.type.FieldEditor;
import com.futureseeds.zookeeper.type.SubscribeType;
import com.github.zkclient.ZkClient;

@Component
public abstract class ZkContext implements InitializingBean, ApplicationContextAware {
//	@Value("${zk.connection}")
	private String zkConnection = "localhost:2181";

	private ZkClient zkClient;
	private ApplicationContext applicationContext;

	private Map<String, Set<FieldEditor>> zkPathMapping;
	
	public static void trace(String message) {
		System.out.println("[ZkContext] "+ message);
	}

	public Map<String, Set<FieldEditor>> getZkPathMapping() {
		return zkPathMapping;
	}
	
	public ZkClient getZkClient() {
		return zkClient;
	}
	
	/**
	 * Scan for ZkValue annotated field in which beans annotated with ZkManage
	 * @return
	 */
	private Map<String, Set<FieldEditor>> scanForFieldsWithZkValueAnnotation() {
		Map<String, Set<FieldEditor>> mapping = new HashMap<String, Set<FieldEditor>>();

		for (Object bean : applicationContext.getBeansWithAnnotation(ZkManage.class).values()) {
			trace("found bean(" + bean.getClass().getName() + ") with ZkManage");

			for (Field field : bean.getClass().getDeclaredFields()) {
				ZkValue annotation = field.getAnnotation(ZkValue.class);
				
				if (annotation != null) {
					trace("found field(" + field.getName() + ") with ZkValue");

					String zkPath = annotation.value();
					
					byte[] dataByte = zkClient.readData(zkPath, true);
					if (dataByte == null) {
						throw new FatalBeanException("no ZkNode found on path \"" + zkPath + "\"");
					}
					String data = new String(dataByte);
					trace("read data(" + data + ") on ZkPath(" + zkPath + ")");
					
					FieldEditor fieldEditor = new FieldEditor(bean, field, annotation.subscribeType(),
							annotation.createStrategy(), applicationContext);
					fieldEditor.set(data);
					
					if (mapping.containsKey(zkPath)) {
						mapping.get(zkPath).add(fieldEditor);
					} else {
						Set<FieldEditor> fieldEditorSet = new HashSet<FieldEditor>();
						fieldEditorSet.add(fieldEditor);
						
						mapping.put(zkPath, fieldEditorSet);
					}
				}
			}
		}
		
		return mapping;
	}
	
	private boolean validateZkPathMapping(Map<String, Set<FieldEditor>> mapping) {
		Map<String, SubscribeType> zkPathSubscribeTypeMapping = new HashMap<String, SubscribeType>();
		Map<String, CreateStrategy> zkPathCreateStrategyMapping = new HashMap<String, CreateStrategy>();

		for (Entry<String, Set<FieldEditor>> entry : mapping.entrySet()) {
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
	 * NOTE:You should make sure that all FieldEditor in the set with the same SubscribeType and the same CreateStrategy
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
	
	@Override
	public void afterPropertiesSet() throws Exception {
		Map<String, Set<FieldEditor>> zkPathMapping = scanForFieldsWithZkValueAnnotation();
		if (validateZkPathMapping(zkPathMapping)) {
			for (Entry<String, Set<FieldEditor>> entry : zkPathMapping.entrySet()) {
				registerZkEvent(entry.getKey(), entry.getValue());
			}
		}
		
		this.zkPathMapping = zkPathMapping;
	}
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		this.zkClient = new ZkClient(zkConnection, 1000);
	}
}
