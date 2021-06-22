IBatis plugin
=============
Reload IBatis configuration after entity change.

IBatis plugin listens for a change and hotswap on all SQLMap files. The SQLMap file  
defined in `org.springframework.orm.ibatis.SqlMapClientFactoryBean.configLocations` and `org.springframework.orm.ibatis.SqlMapClientFactoryBean.mappingLocations`, Such as:

	<bean id="sqlMapClient" class="org.springframework.orm.ibatis.SqlMapClientFactoryBean">
    	<property name="configLocations">
			<value>classpath:conf/ibatis/sqlMapConfig.xml</value>
		</property>    
		<property name="mappingLocations">
			<value>classpath:sqlmap/**/*.xml</value>
		</property>
        ......
	</bean>