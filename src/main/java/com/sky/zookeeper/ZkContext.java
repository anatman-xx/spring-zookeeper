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
import com.sky.zookeeper.type.CreateStrategy;
import com.sky.zookeeper.type.FieldEditor;
import com.sky.zookeeper.type.MethodInvoker;
import com.sky.zookeeper.type.Modifier;
import com.sky.zookeeper.type.SubscribeType;
import com.sky.zookeeper.watcher.ZkDataChangeWatcher;
import com.sky.zookeeper.watcher.ZkElectionListener;

@Component
public abstract class ZkContext implements InitializingBean, ApplicationContextAware {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZkContext.class);

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
					&& field.getType().equals(Boolean.class);
		}
	};
	
	public static final MethodFilter ZKLEADER_ANNOTATED_METHODS = new MethodFilter() {
		@Override
		public boolean matches(Method method) {
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (parameterTypes == null || parameterTypes.length != 1 || !parameterTypes[0].equals(String.class)) {
				return false;
			}

			return ReflectionUtils.USER_DECLARED_METHODS.matches(method) && method.isAnnotationPresent(ZkLeader.class);
		}
	};

	private ApplicationContext applicationContext;

	private CuratorFramework zkClient;
	private Map<String, Set<Modifier>> zkPathModifierMapping = new HashMap<String, Set<Modifier>>();
	private Map<String, Set<Modifier>> zkPathLeaderModifierMapping = new HashMap<String, Set<Modifier>>();
	private Map<String, LeaderSelector> zkPathLeaderSelectorMapping = new HashMap<String, LeaderSelector>();

	public abstract String getZkConnection();
	public abstract Integer getZkConnectionTimeout();

	public CuratorFramework getZkClient() {
		return zkClient;
	}
	
	public void shutdown() {
		for (Entry<String, LeaderSelector> entry : zkPathLeaderSelectorMapping.entrySet()) {
			entry.getValue().close();
		}
	}

	/**
	 * Scan for Zk* annotated field in which beans annotated with ZkManage
	 */
	private void scanForFieldsAndMethods() {
		LOGGER.trace("scanning for fields and methods with zk* annotation...");

		for (final Object bean : applicationContext.getBeansWithAnnotation(ZkManage.class).values()) {
			LOGGER.trace("found bean(" + bean.getClass().getName() + ") with ZkManage");

			ReflectionUtils.doWithFields(bean.getClass(), new FieldCallback() {
				@Override
				public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found field(" + field.getName() + ") with ZkValue");

					registerZkValue(bean, field, true);
				}
			}, ZKVALUE_ANNOTATED_FIELDS);
			
			ReflectionUtils.doWithMethods(bean.getClass(), new MethodCallback() {
				@Override
				public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found method(" + method.getName() + ") with ZkValue");
					
					registerZkValue(bean, method, true);
				}
			}, ZKVALUE_ANNOTATED_METHODS);
			
			ReflectionUtils.doWithFields(bean.getClass(), new FieldCallback() {
				@Override
				public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found field(" + field.getName() + ") with ZkLeader");

					registerZkLeader(bean, field);
				}
			}, ZKLEADER_ANNOTATED_FIELDS);
			
			ReflectionUtils.doWithMethods(bean.getClass(), new MethodCallback() {
				@Override
				public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
					LOGGER.trace("found method(" + method.getName() + ") with ZkLeader");
					
					registerZkLeader(bean, method);
				}
			}, ZKLEADER_ANNOTATED_METHODS);
		}
	}

	private void validateZkPathMapping() {
		LOGGER.trace("validating ZkPathMapping...");

		Map<String, SubscribeType> zkPathSubscribeTypeMapping = new HashMap<String, SubscribeType>();
		Map<String, CreateStrategy> zkPathCreateStrategyMapping = new HashMap<String, CreateStrategy>();

		for (Entry<String, Set<Modifier>> entry : zkPathModifierMapping.entrySet()) {
			for (Modifier modifier : entry.getValue()) {
				if (zkPathSubscribeTypeMapping.containsKey(entry.getKey())
						&& zkPathSubscribeTypeMapping.get(entry.getKey()) != modifier.getSubscribeType()) {
					throw new FatalBeanException("Confilct SubscribeType");
				}

				if (zkPathCreateStrategyMapping.containsKey(entry.getKey())
						&& zkPathCreateStrategyMapping.get(entry.getKey()) != modifier.getCreateStrategy()) {
					throw new FatalBeanException("Confilct CreateStrategy");
				}
			}
		}
	}

	/**
	 * NOTE:You should make sure that all FieldEditor in the set with the same SubscribeType and the same CreateStrategy
	 */
	private void registerEventWatcher(String zkPath, Set<Modifier> modifierSet) {
		Modifier fieldEditor = (Modifier) modifierSet.toArray()[0];

		switch (fieldEditor.getSubscribeType()) {
		case DATA_CHANGE:
			try {
				zkClient.getData()
					.usingWatcher(new ZkDataChangeWatcher(zkClient, modifierSet))
					.forPath(zkPath);
			} catch (Exception e) {
				throw new FatalBeanException("register zkEvent failed (on path \"" + zkPath + "\")");
			}

			break;

		default:
			break;
		}
	}

	private void registerElectionListener(String zkLeaderElectionPath, Set<Modifier> modifierSet) {
		LeaderSelector leaderSelector = new LeaderSelector(zkClient, zkLeaderElectionPath, new ZkElectionListener(
				zkLeaderElectionPath, modifierSet));
		leaderSelector.start();

		zkPathLeaderSelectorMapping.put(zkLeaderElectionPath, leaderSelector);
	}
	
	private void registerZkLeader(Object bean, Field field) {
		ZkLeader annotation = field.getAnnotation(ZkLeader.class);
		String zkLeaderElectionPath = annotation.value();
		
		FieldEditor fieldEditor = new FieldEditor(bean, field, applicationContext, SubscribeType.DATA_CHANGE,
				CreateStrategy.CONSTRUCTOR);
		
		if (zkPathLeaderModifierMapping.containsKey(zkLeaderElectionPath)) {
			zkPathLeaderModifierMapping.get(zkLeaderElectionPath).add(fieldEditor);
		} else {
			Set<Modifier> modifierSet = new HashSet<Modifier>();
			modifierSet.add(fieldEditor);

			zkPathLeaderModifierMapping.put(zkLeaderElectionPath, modifierSet);
		}
	}

	private void registerZkLeader(Object bean, Method method) {
		ZkLeader annotation = method.getAnnotation(ZkLeader.class);
		String zkLeaderElectionPath = annotation.value();
		
		MethodInvoker methodInvoker = new MethodInvoker(bean, method, applicationContext);
		
		if (zkPathLeaderModifierMapping.containsKey(zkLeaderElectionPath)) {
			zkPathLeaderModifierMapping.get(zkLeaderElectionPath).add(methodInvoker);
		} else {
			Set<Modifier> modifierSet = new HashSet<Modifier>();
			modifierSet.add(methodInvoker);

			zkPathLeaderModifierMapping.put(zkLeaderElectionPath, modifierSet);
		}
	}

	private void registerZkValue(Object bean, Field field, boolean initial) {
		ZkValue annotation = field.getAnnotation(ZkValue.class);
		String zkPath = annotation.value();
		
		FieldEditor fieldEditor = new FieldEditor(bean, field, applicationContext, annotation.subscribeType(),
				annotation.createStrategy());

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

		if (zkPathModifierMapping.containsKey(zkPath)) {
			zkPathModifierMapping.get(zkPath).add(fieldEditor);
		} else {
			Set<Modifier> modifierSet = new HashSet<Modifier>();
			modifierSet.add(fieldEditor);

			zkPathModifierMapping.put(zkPath, modifierSet);
		}
	}
	
	private void registerZkValue(Object bean, Method method, boolean initial) {
		MethodInvoker methodInvoker = new MethodInvoker(bean, method, applicationContext);

		ZkValue annotation = method.getAnnotation(ZkValue.class);
		String zkPath = annotation.value();
		
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
		
		if (zkPathModifierMapping.containsKey(zkPath)) {
			zkPathModifierMapping.get(zkPath).add(methodInvoker);
		} else {
			Set<Modifier> methodInvokerSet = new HashSet<Modifier>();
			methodInvokerSet.add(methodInvoker);

			zkPathModifierMapping.put(zkPath, methodInvokerSet);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		scanForFieldsAndMethods();
		validateZkPathMapping();
		
		for (Entry<String, Set<Modifier>> entry : zkPathModifierMapping.entrySet()) {
			registerEventWatcher(entry.getKey(), entry.getValue());
		}
		
		for (Entry<String, Set<Modifier>> entry : zkPathLeaderModifierMapping.entrySet()) {
			registerElectionListener(entry.getKey(), entry.getValue());
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
