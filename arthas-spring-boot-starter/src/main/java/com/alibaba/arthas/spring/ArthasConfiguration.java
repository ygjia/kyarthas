package com.alibaba.arthas.spring;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;

import com.taobao.arthas.agent.attach.ArthasAgent;

/**
 *
 * @author hengyunabc 2020-06-22
 *
 */
@ConditionalOnProperty(name = "spring.arthas.enabled", matchIfMissing = true)
@EnableConfigurationProperties({ ArthasProperties.class })
public class ArthasConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(ArthasConfiguration.class);

	@Autowired
	ConfigurableEnvironment environment;

	/**
	 * <pre>
	 * 1. 提取所有以 arthas.* 开头的配置项，再统一转换为Arthas配置
	 * 2. 避免某些配置在新版本里支持，但在ArthasProperties里没有配置的情况。
	 * </pre>
	 */
	@ConfigurationProperties(prefix = "arthas")
	@ConditionalOnMissingBean(name="arthasConfigMap")
	@Bean
	public HashMap<String, String> arthasConfigMap() {
		return new HashMap<String, String>();
	}

	/**
	 * KE with arthas-spring-boot-starter can not get params to `arthasConfigMap`,
	 * reinit `arthasConfigMap` before arthasAgent.init();
	 * see KE-37696
	 * @param arthasConfigMap
	 */

	public void reinitArthasConfigMap(ArthasProperties arthasProperties, Map<String, String> arthasConfigMap) {
		if (!arthasConfigMap.containsKey("httpPort") && arthasProperties.getHttpPort() != 0) {
			arthasConfigMap.put("httpPort", String.valueOf(arthasProperties.getHttpPort()));
		}
		if (!arthasConfigMap.containsKey("telnetPort") && arthasProperties.getTelnetPort() != 0) {
			arthasConfigMap.put("telnetPort", String.valueOf(arthasProperties.getTelnetPort()));
		}
		if (!arthasConfigMap.containsKey("ip") && arthasProperties.getIp() != null) {
			arthasConfigMap.put("ip", arthasProperties.getIp());
		}
		if (!arthasConfigMap.containsKey("tunnelServer") && arthasProperties.getTunnelServer() != null) {
			arthasConfigMap.put("tunnelServer", arthasProperties.getTunnelServer());
		}
		if (!arthasConfigMap.containsKey("appName") && arthasProperties.getAppName() != null) {
			arthasConfigMap.put("appName", arthasProperties.getAppName());
		}
	}

	@ConditionalOnMissingBean
	@Bean
	public ArthasAgent arthasAgent(@Autowired @Qualifier("arthasConfigMap") Map<String, String> arthasConfigMap,
			@Autowired ArthasProperties arthasProperties) throws Throwable {
        arthasConfigMap = StringUtils.removeDashKey(arthasConfigMap);
        ArthasProperties.updateArthasConfigMapDefaultValue(arthasConfigMap);
		reinitArthasConfigMap(arthasProperties, arthasConfigMap);

        /**
         * @see org.springframework.boot.context.ContextIdApplicationContextInitializer#getApplicationId(ConfigurableEnvironment)
         */
        String appName = environment.getProperty("spring.application.name");
        if (arthasConfigMap.get("appName") == null && appName != null) {
            arthasConfigMap.put("appName", appName);
        }

		// 给配置全加上前缀
		Map<String, String> mapWithPrefix = new HashMap<String, String>(arthasConfigMap.size());
		for (Entry<String, String> entry : arthasConfigMap.entrySet()) {
			mapWithPrefix.put("arthas." + entry.getKey(), entry.getValue());
		}

		final ArthasAgent arthasAgent = new ArthasAgent(mapWithPrefix, arthasProperties.getHome(),
				arthasProperties.isSlientInit(), null);

		arthasAgent.init();
		logger.info("Arthas agent start success.");
		return arthasAgent;

	}
}
