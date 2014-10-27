package com.sky.zookeeper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.ReflectionUtils.MethodFilter;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.recipes.leader.LeaderSelector;
import com.netflix.curator.retry.RetryNTimes;
import com.sky.zookeeper.annotation.ZkLeader;
import com.sky.zookeeper.annotation.ZkManage;
import com.sky.zookeeper.annotation.ZkValue;
import com.sky.zookeeper.handler.ZkDataChangeEventHandler;
import com.sky.zookeeper.handler.ZkLeaderHandler;
import com.sky.zookeeper.type.CreateStrategy;
import com.sky.zookeeper.type.FieldEditor;
import com.sky.zookeeper.type.MethodInvoker;
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
	
	public static final MethodFilter ZKVALUE_ANNOTATED_METHODS = new MethodFilter() {
		@Override
		public boolean matches(Method method) {
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (parameterTypes == null || parameterTypes.length != 1 || !parameterTypes[0].equals(String.class)) {
				return false;
			}

			return ReflectionUtils.USER_DECLARED_METHODS.matches(method) && method.isAnnotationPresent(ZkValue.class);
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

	private CuratorFramework zkClient;
	private Map<String, Set<FieldEditor>> zkPathFieldEditorMapping = new HashMap<String, Set<FieldEditor>>();
	private Map<String, Set<MethodInvoker>> zkPathMethodMapping = new HashMap<String, Set<MethodInvoker>>();
	private Map<String, Set<FieldEditor>> zkPathLeaderFieldEditorMapping = new HashMap<String, Set<FieldEditor>>();
	private Map<String, LeaderSelector> zkPathLeaderSelectorMapping = new HashMap<String, LeaderSelector>();

	public abstract String getZkConnection();
	public abstract Integer getZkConnectionTimeout();

	public CuratorFramework getZkClient() {
		return zkClient;
	}

	public Map<String, Set<FieldEditor>> getZkPathMapping() {
		return zkPathFieldEditorMapping;
	}

	/**
	 * Scan for Zk* annotated field in which beans annotated with ZkManage
	 */
	private void scanForFieldsAndMethods() {
		for (final Object bean : applicationContext.getBeansWithAnnotation(ZkManage.class).values()) {
			LOGGER.trace("found bean(" + bean.getClass().getName() + ") with ZkManage");

			ReflectionUtils.doWithFields(bean.getClass(), new FieldCallback() {
				@Override
				public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found field(" + field.getName() + ") with ZkValue");

					registerZkValueField(bean, field, true);
				}
			}, ZKVALUE_ANNOTATED_FIELDS);
			
			ReflectionUtils.doWithMethods(bean.getClass(), new MethodCallback() {
				@Override
				public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found method(" + method.getName() + ") with ZkValue");
					
					registerZkValueMethod(bean, method, true);
				}
			}, ZKVALUE_ANNOTATED_METHODS);
			
			ReflectionUtils.doWithFields(bean.getClass(), new FieldCallback() {
				@Override
				public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found field(" + field.getName() + ") with ZkLeader");

					registerZkLeaderField(bean, field, true);
				}
			}, ZKLEADER_ANNOTATED_FIELDS);
		}
	}

	private void validateZkPathMapping() {
		Map<String, SubscribeType> zkPathSubscribeTypeMapping = new HashMap<String, SubscribeType>();
		Map<String, CreateStrategy> zkPathCreateStrategyMapping = new HashMap<String, CreateStrategy>();

		for (Entry<String, Set<FieldEditor>> entry : zkPathFieldEditorMapping.entrySet()) {
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
	}

	/**
	 * NOTE:You should make sure that all FieldEditor in the set with the same SubscribeType and the same CreateStrategy
	 */
	private void registerZkFieldEvent(String zkPath, Set<FieldEditor> fieldEditorSet) {
		FieldEditor fieldEditor = (FieldEditor) fieldEditorSet.toArray()[0];

		switch (fieldEditor.getSubscribeType()) {
		case DATA_CHANGE:
			try {
				zkClient.getData()
					.usingWatcher(new ZkDataChangeEventHandler(zkClient, fieldEditorSet))
					.forPath(zkPath);
			} catch (Exception e) {
				throw new FatalBeanException("register zkEvent failed (on path \"" + zkPath + "\")");
			}

			break;

		default:
			break;
		}
	}

	private void registerZkMethodEvent(String zkPath, Set<MethodInvoker> methodInvokerSet) {
		try {
			zkClient.getData()
				.usingWatcher(new ZkDataChangeEventHandler(zkClient, methodInvokerSet))
				.forPath(zkPath);
		} catch (Exception e) {
			throw new FatalBeanException("register zkEvent failed (on path \"" + zkPath + "\")");
		}
	}
	
	private void registerZkElection(String zkLeaderElectionPath, Set<FieldEditor> fieldEditorSet) {
		LeaderSelector leaderSelector = new LeaderSelector(zkClient, zkLeaderElectionPath, new ZkLeaderHandler(
				zkLeaderElectionPath, fieldEditorSet));
		leaderSelector.start();
		
		// TODO:initial value maybe wrong

		zkPathLeaderSelectorMapping.put(zkLeaderElectionPath, leaderSelector);
	}
	
	private void registerZkLeaderField(Object bean, Field field, boolean initial) {
		ZkLeader annotation = field.getAnnotation(ZkLeader.class);
		String zkLeaderElectionPath = annotation.value();
		
		FieldEditor fieldEditor = new FieldEditor(bean, field, SubscribeType.DATA_CHANGE, CreateStrategy.CONSTRUCTOR,
				applicationContext);
		
		if (initial) {
			fieldEditor.set("false");
		}

		if (zkPathLeaderFieldEditorMapping.containsKey(zkLeaderElectionPath)) {
			zkPathLeaderFieldEditorMapping.get(zkLeaderElectionPath).add(fieldEditor);
		} else {
			Set<FieldEditor> fieldEditorSet = new HashSet<FieldEditor>();
			fieldEditorSet.add(fieldEditor);

			zkPathLeaderFieldEditorMapping.put(zkLeaderElectionPath, fieldEditorSet);
		}
	}

	private void registerZkValueField(Object bean, Field field, boolean initial) {
		ZkValue annotation = field.getAnnotation(ZkValue.class);
		String zkPath = annotation.value();

		FieldEditor fieldEditor = new FieldEditor(bean, field, annotation.subscribeType(), annotation.createStrategy(),
				applicationContext);

		if (initial) {
			byte[] dataByte = null;

			try {
				dataByte = zkClient.getData().forPath(zkPath);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (dataByte == null) {
				throw new FatalBeanException("no data found on path \"" + zkPath + "\"");
			}

			String data = new String(dataByte);

			LOGGER.trace("read data(" + data + ") on ZkPath(" + zkPath + ")");
			fieldEditor.set(data);
		}

		if (zkPathFieldEditorMapping.containsKey(zkPath)) {
			zkPathFieldEditorMapping.get(zkPath).add(fieldEditor);
		} else {
			Set<FieldEditor> fieldEditorSet = new HashSet<FieldEditor>();
			fieldEditorSet.add(fieldEditor);

			zkPathFieldEditorMapping.put(zkPath, fieldEditorSet);
		}
	}
	
	private void registerZkValueMethod(Object bean, Method method, boolean initial) {
		ZkValue annotation = method.getAnnotation(ZkValue.class);
		String zkPath = annotation.value();
		
		MethodInvoker methodInvoker = new MethodInvoker(bean, method);

		if (initial) {
			byte[] dataByte = null;

			try {
				dataByte = zkClient.getData().forPath(zkPath);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (dataByte == null) {
				throw new FatalBeanException("no data found on path \"" + zkPath + "\"");
			}

			String data = new String(dataByte);

			LOGGER.trace("read data(" + data + ") on ZkPath(" + zkPath + ")");

			methodInvoker.invoke(data);
		}
		
		if (zkPathMethodMapping.containsKey(zkPath)) {
			zkPathMethodMapping.get(zkPath).add(methodInvoker);
		} else {
			Set<MethodInvoker> methodInvokerSet = new HashSet<MethodInvoker>();
			methodInvokerSet.add(methodInvoker);

			zkPathMethodMapping.put(zkPath, methodInvokerSet);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		scanForFieldsAndMethods();
		validateZkPathMapping();
		
		for (Entry<String, Set<FieldEditor>> entry : zkPathFieldEditorMapping.entrySet()) {
			registerZkFieldEvent(entry.getKey(), entry.getValue());
		}
		
		for (Entry<String, Set<MethodInvoker>> entry : zkPathMethodMapping.entrySet()) {
			registerZkMethodEvent(entry.getKey(), entry.getValue());
		}
		
		for (Entry<String, Set<FieldEditor>> entry : zkPathLeaderFieldEditorMapping.entrySet()) {
			registerZkElection(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		this.zkClient = CuratorFrameworkFactory.builder()
				.connectString(getZkConnection())
				.connectionTimeoutMs(getZkConnectionTimeout())
				.retryPolicy(new RetryNTimes(100, 10000))
				.build();
		
		this.zkClient.start();
	}
}
