package com.sky.zookeeper.type;

public enum CreateStrategy {
	/**
	 * None
	 */
	NONE,

	/**
	 * Get value by constructing new instance
	 */
	CONSTRUCTOR,
	
	/**
	 * Get value from exist beans from application context
	 */
	BEAN
}
