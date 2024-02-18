/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.event.ServiceConfigExportedEvent;
import org.apache.dubbo.config.event.ServiceConfigUnexportedEvent;
import org.apache.dubbo.config.invoker.DelegateProviderMetaDataInvoker;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.event.Event;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.metadata.ServiceNameMapping;
import org.apache.dubbo.registry.client.metadata.MetadataUtils;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.cluster.ConfiguratorFactory;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.model.ServiceRepository;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_IP_TO_BIND;
import static org.apache.dubbo.common.constants.CommonConstants.LOCALHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.MAPPING_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METADATA_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.REGISTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.CommonConstants.REVISION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SERVICE_NAME_MAPPING_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.SERVICE_REGISTRY_PROTOCOL;
import static org.apache.dubbo.common.utils.NetUtils.getAvailablePort;
import static org.apache.dubbo.common.utils.NetUtils.getLocalHost;
import static org.apache.dubbo.common.utils.NetUtils.isInvalidLocalHost;
import static org.apache.dubbo.common.utils.NetUtils.isInvalidPort;
import static org.apache.dubbo.config.Constants.DUBBO_IP_TO_REGISTRY;
import static org.apache.dubbo.config.Constants.DUBBO_PORT_TO_BIND;
import static org.apache.dubbo.config.Constants.DUBBO_PORT_TO_REGISTRY;
import static org.apache.dubbo.config.Constants.SCOPE_NONE;
import static org.apache.dubbo.remoting.Constants.BIND_IP_KEY;
import static org.apache.dubbo.remoting.Constants.BIND_PORT_KEY;
import static org.apache.dubbo.rpc.Constants.GENERIC_KEY;
import static org.apache.dubbo.rpc.Constants.LOCAL_PROTOCOL;
import static org.apache.dubbo.rpc.Constants.PROXY_KEY;
import static org.apache.dubbo.rpc.Constants.SCOPE_KEY;
import static org.apache.dubbo.rpc.Constants.SCOPE_LOCAL;
import static org.apache.dubbo.rpc.Constants.SCOPE_REMOTE;
import static org.apache.dubbo.rpc.Constants.TOKEN_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;

/**
 *
 * @param <T>
 */

/**
 *         ServiceConfig serviceConfig = new ServiceConfig();
 *         //设置应用程序配置
 *         serviceConfig.setApplication(new ApplicationConfig("first-dubbo-provider"));
 *         //设置服务注册中心信息，可知服务注册中心使用了Zookeeper，并且zookeeper的地址为127.0.0.1，启动端口为2181，服务提供方会将服务注册到该中心
 *         RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
 *         serviceConfig.setRegistry(registryConfig);
 *         //设置接口与实现类
 *         serviceConfig.setInterface("");
 *         serviceConfig.setRef(null);
 *         //设置服务分组与版本，在Dubbo中“服务接口+服务分组+服务版本”唯一确定一个服务，同一个服务接口可以有不同的版本以便服务升级，另外每个服务接口可以属于不同分组，
 *         //所以当调用方消费服务时一定要设置正确的分组与版本
 *         serviceConfig.setVersion("1.0.0");
 *         serviceConfig.setGroup("dubbo");
 *         //设置线程池策略
 *         Map<String, String> parameters = new HashMap<>();
 *         parameters.put("threadpool","mythreadpool");
 *         serviceConfig.setParameters(parameters);
 *         //导出服务，启动NettyServer监听链接请求，并将服务注册到服务注册中心
 *         serviceConfig.export();
 * @param <T>
 */
public class ServiceConfig<T> extends ServiceConfigBase<T> {

    private static final long serialVersionUID = -412700146501624375L;

    public static final Logger logger = LoggerFactory.getLogger(ServiceConfig.class);

    /**
     * A random port cache, the different protocols who has no port specified have different random port
     */
    private static final Map<String, Integer> RANDOM_PORT_MAP = new HashMap<String, Integer>();

    /**
     * A delayed exposure service timer
     */
    private static final ScheduledExecutorService DELAY_EXPORT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("DubboServiceDelayExporter", true));

    private String serviceName;

    private static final Protocol PROTOCOL = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

    /**
     * A {@link ProxyFactory} implementation that will generate a exported service proxy,the JavassistProxyFactory is its
     * default implementation
     */
    private static final ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    /**
     * Whether the provider has been exported
     */
    private transient volatile boolean exported;

    /**
     * The flag whether a service has unexported ,if the method unexported is invoked, the value is true
     */
    private transient volatile boolean unexported;

    private DubboBootstrap bootstrap;

    /**
     * The exported services
     */
    private final List<Exporter<?>> exporters = new ArrayList<Exporter<?>>();

    private static final String CONFIG_INITIALIZER_PROTOCOL = "configInitializer://";

    private static final String TRUE_VALUE = "true";

    private static final String FALSE_VALUE = "false";

    private static final String STUB_SUFFIX = "Stub";

    private static final String LOCAL_SUFFIX = "Local";

    private static final String CONFIG_POST_PROCESSOR_PROTOCOL = "configPostProcessor://";

    private static final String RETRY_SUFFIX = ".retry";

    private static final String RETRIES_SUFFIX = ".retries";

    private static final String ZERO_VALUE = "0";

    public ServiceConfig() {
    }

    public ServiceConfig(Service service) {
        super(service);
    }

    @Parameter(excluded = true)
    @Override
    public boolean isExported() {
        return exported;
    }

    @Parameter(excluded = true)
    @Override
    public boolean isUnexported() {
        return unexported;
    }

    @Override
    public void unexport() {
        if (!exported) {
            return;
        }
        if (unexported) {
            return;
        }
        if (!exporters.isEmpty()) {
            for (Exporter<?> exporter : exporters) {
                try {
                    exporter.unexport();
                } catch (Throwable t) {
                    logger.warn("Unexpected error occurred when unexport " + exporter, t);
                }
            }
            exporters.clear();
        }
        unexported = true;

        // dispatch a ServiceConfigUnExportedEvent since 2.7.4
        dispatch(new ServiceConfigUnexportedEvent(this));
    }

    /**
     * 激活发布服务
     */
    @Override
    public synchronized void export() {
        if (bootstrap == null) {
            bootstrap = DubboBootstrap.getInstance();
            // compatible with api call.
            if (null != this.getRegistry()) {
                bootstrap.registries(this.getRegistries());
            }
            bootstrap.initialize();
        }

        checkAndUpdateSubConfigs();

        initServiceMetadata(provider);
        serviceMetadata.setServiceType(getInterfaceClass());
        serviceMetadata.setTarget(getRef());
        serviceMetadata.generateServiceKey();

        //是否需要导出服务
        if (!shouldExport()) {
            return;
        }

        //这里是延迟发布
        if (shouldDelay()) {
            DELAY_EXPORT_EXECUTOR.schedule(() -> {
                try {
                    // Delay export server should print stack trace if there are exception occur.
                    this.doExport();
                } catch (Exception e) {
                    logger.error("delay export server occur exception, please check it.", e);
                }
            }, getDelay(), TimeUnit.MILLISECONDS);
        } else {
            //直接发布
            doExport();
        }

        exported();
    }

    public void exported() {
        List<URL> exportedURLs = this.getExportedUrls();
        exportedURLs.forEach(url -> {
            // dubbo2.7.x does not register serviceNameMapping information with the registry by default.
            // Only when the user manually turns on the service introspection, can he register with the registration center.
            if (url.getParameters().containsKey(SERVICE_NAME_MAPPING_KEY)) {
                Map<String, String> parameters = getApplication().getParameters();
                ServiceNameMapping.getExtension(parameters != null ? parameters.get(MAPPING_KEY) : null).map(url);
            }
        });
        // dispatch a ServiceConfigExportedEvent since 2.7.4
        dispatch(new ServiceConfigExportedEvent(this));
    }

    private void checkAndUpdateSubConfigs() {
        // Use default configs defined explicitly with global scope
        completeCompoundConfigs();
        checkDefault();
        checkProtocol();
        // init some null configuration.
        List<ConfigInitializer> configInitializers = ExtensionLoader.getExtensionLoader(ConfigInitializer.class)
                .getActivateExtension(URL.valueOf(CONFIG_INITIALIZER_PROTOCOL), (String[]) null);
        configInitializers.forEach(e -> e.initServiceConfig(this));

        // if protocol is not injvm checkRegistry
        if (!isOnlyInJvm()) {
            checkRegistry();
        }
        this.refresh();

        if (StringUtils.isEmpty(interfaceName)) {
            throw new IllegalStateException("<dubbo:service interface=\"\" /> interface not allow null!");
        }

        if (ref instanceof GenericService) {
            interfaceClass = GenericService.class;
            if (StringUtils.isEmpty(generic)) {
                generic = TRUE_VALUE;
            }
        } else {
            try {
                interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            checkInterfaceAndMethods(interfaceClass, getMethods());
            checkRef();
            generic = FALSE_VALUE;
        }

        checkStubAndLocal(interfaceClass);
        ConfigValidationUtils.checkMock(interfaceClass, this);
        ConfigValidationUtils.validateServiceConfig(this);
        postProcessConfig();
    }


    protected synchronized void doExport() {
        if (unexported) {
            throw new IllegalStateException("The service " + interfaceClass.getName() + " has already unexported!");
        }
        if (exported) {
            return;
        }
        exported = true;

        if (StringUtils.isEmpty(path)) {
            path = interfaceName;
        }
        doExportUrls();
        bootstrap.setReady(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doExportUrls() {
        ServiceRepository repository = ApplicationModel.getServiceRepository();
        ServiceDescriptor serviceDescriptor = repository.registerService(getInterfaceClass());
        repository.registerProvider(
                getUniqueServiceName(),
                ref,
                serviceDescriptor,
                this,
                serviceMetadata
        );

        //加载所有的服务注册中心对象，一个服务可以被注册到多个服务注册中心
        List<URL> registryURLs = ConfigValidationUtils.loadRegistries(this, true);

        int protocolConfigNum = protocols.size();
        for (ProtocolConfig protocolConfig : protocols) {
            String pathKey = URL.buildKey(getContextPath(protocolConfig)
                    .map(p -> p + "/" + path)
                    .orElse(path), group, version);
            // In case user specified path, register service one more time to map it to path.
            repository.registerService(pathKey, interfaceClass);
            doExportUrlsFor1Protocol(protocolConfig, registryURLs, protocolConfigNum);
        }
    }

    private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs, int protocolConfigNum) {
        //1、解析MethodConfig配置
        String name = protocolConfig.getName();
        if (StringUtils.isEmpty(name)) {
            name = DUBBO;
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put(SIDE_KEY, PROVIDER_SIDE);

        ServiceConfig.appendRuntimeParameters(map);
        AbstractConfig.appendParameters(map, getMetrics());
        AbstractConfig.appendParameters(map, getApplication());
        AbstractConfig.appendParameters(map, getModule());
        // remove 'default.' prefix for configs from ProviderConfig
        // appendParameters(map, provider, Constants.DEFAULT_KEY);
        AbstractConfig.appendParameters(map, provider);
        AbstractConfig.appendParameters(map, protocolConfig);
        AbstractConfig.appendParameters(map, this);
        MetadataReportConfig metadataReportConfig = getMetadataReportConfig();
        if (metadataReportConfig != null && metadataReportConfig.isValid()) {
            map.putIfAbsent(METADATA_KEY, REMOTE_METADATA_STORAGE_TYPE);
        }
        if (CollectionUtils.isNotEmpty(getMethods())) {
            for (MethodConfig method : getMethods()) {
                AbstractConfig.appendParameters(map, method, method.getName());
                String retryKey = method.getName() + RETRY_SUFFIX;
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    if (FALSE_VALUE.equals(retryValue)) {
                        map.put(method.getName() + RETRIES_SUFFIX, ZERO_VALUE);
                    }
                }
                List<ArgumentConfig> arguments = method.getArguments();
                if (CollectionUtils.isNotEmpty(arguments)) {
                    for (ArgumentConfig argument : arguments) {
                        // convert argument type
                        if (argument.getType() != null && argument.getType().length() > 0) {
                            Method[] methods = interfaceClass.getMethods();
                            // visit all methods
                            if (methods.length > 0) {
                                for (int i = 0; i < methods.length; i++) {
                                    String methodName = methods[i].getName();
                                    // target the method, and get its signature
                                    if (methodName.equals(method.getName())) {
                                        Class<?>[] argtypes = methods[i].getParameterTypes();
                                        // one callback in the method
                                        if (argument.getIndex() != -1) {
                                            if (argtypes[argument.getIndex()].getName().equals(argument.getType())) {
                                                AbstractConfig
                                                        .appendParameters(map, argument, method.getName() + "." + argument.getIndex());
                                            } else {
                                                throw new IllegalArgumentException(
                                                        "Argument config error : the index attribute and type attribute not match :index :" +
                                                                argument.getIndex() + ", type:" + argument.getType());
                                            }
                                        } else {
                                            // multiple callbacks in the method
                                            for (int j = 0; j < argtypes.length; j++) {
                                                Class<?> argclazz = argtypes[j];
                                                if (argclazz.getName().equals(argument.getType())) {
                                                    AbstractConfig.appendParameters(map, argument, method.getName() + "." + j);
                                                    if (argument.getIndex() != -1 && argument.getIndex() != j) {
                                                        throw new IllegalArgumentException(
                                                                "Argument config error : the index attribute and type attribute not match :index :" +
                                                                        argument.getIndex() + ", type:" + argument.getType());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (argument.getIndex() != -1) {
                            AbstractConfig.appendParameters(map, argument, method.getName() + "." + argument.getIndex());
                        } else {
                            throw new IllegalArgumentException(
                                    "Argument config must set index or type attribute.eg: <dubbo:argument index='0' .../> or <dubbo:argument type=xxx .../>");
                        }

                    }
                }
            } // end of methods for
        }

        //2、如果是泛型调用，设置泛型类型
        if (ProtocolUtils.isGeneric(generic)) {
            map.put(GENERIC_KEY, generic);
            map.put(METHODS_KEY, ANY_VALUE);
        } else {
            //3、正常调用设置拼接URL的参数
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                map.put(REVISION_KEY, revision);
            }

            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put(METHODS_KEY, ANY_VALUE);
            } else {
                map.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }

        /**
         * Here the token value configured by the provider is used to assign the value to ServiceConfig#token
         */
        if (ConfigUtils.isEmpty(token) && provider != null) {
            token = provider.getToken();
        }

        if (!ConfigUtils.isEmpty(token)) {
            if (ConfigUtils.isDefault(token)) {
                map.put(TOKEN_KEY, UUID.randomUUID().toString());
            } else {
                map.put(TOKEN_KEY, token);
            }
        }
        //init serviceMetadata attachments
        serviceMetadata.getAttachments().putAll(map);

        // export service
        //4、拼接URL对象
        String host = findConfigedHosts(protocolConfig, registryURLs, map);
        Integer port = findConfigedPorts(protocolConfig, name, map, protocolConfigNum);
        //把参数封装为URL
        URL url = new URL(name, host, port, getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), map);

        // You can customize Configurator to append extra parameters
        if (ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .hasExtension(url.getProtocol())) {
            url = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                    .getExtension(url.getProtocol()).getConfigurator(url).configure(url);
        }

        //5、导出服务，本地服务、远程服务
        String scope = url.getParameter(SCOPE_KEY);
        // don't export when none is configured
        //如果scope为none则不导出服务
        if (!SCOPE_NONE.equalsIgnoreCase(scope)) {

            // export to local if the config is not remote (export to remote only when config is remote)
            //5.1、如果scope不是remote，则导出本地服务
            if (!SCOPE_REMOTE.equalsIgnoreCase(scope)) {
                exportLocal(url);
            }
            // export to remote if the config is not local (export to local only when config is local)
            //5.2、如果scope不是local，则导出远程服务
            if (!SCOPE_LOCAL.equalsIgnoreCase(scope)) {
                //5.2.1、如果有服务注册中心地址
                if (CollectionUtils.isNotEmpty(registryURLs)) {
                    for (URL registryURL : registryURLs) {
                        if (SERVICE_REGISTRY_PROTOCOL.equals(registryURL.getProtocol())) {
                            url = url.addParameterIfAbsent(SERVICE_NAME_MAPPING_KEY, "true");
                        }

                        //if protocol is only injvm ,not register
                        if (LOCAL_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
                            continue;
                        }
                        url = url.addParameterIfAbsent(DYNAMIC_KEY, registryURL.getParameter(DYNAMIC_KEY));
                        URL monitorUrl = ConfigValidationUtils.loadMonitor(this, registryURL);
                        if (monitorUrl != null) {
                            url = url.addParameterAndEncoded(MONITOR_KEY, monitorUrl.toFullString());
                        }
                        if (logger.isInfoEnabled()) {
                            if (url.getParameter(REGISTER_KEY, true)) {
                                logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " +
                                        registryURL);
                            } else {
                                logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                            }
                        }

                        // For providers, this is used to enable custom proxy to generate invoker
                        String proxy = url.getParameter(PROXY_KEY);
                        if (StringUtils.isNotEmpty(proxy)) {
                            registryURL = registryURL.addParameter(PROXY_KEY, proxy);
                        }

                        //ProxyFactory的适配器类ProxyFactory$Adaptive，getInvoker方法内部根据URL的proxy类型选择具体的代理工厂
                        //这里默认proxy类型为javassist，所以又调用了JavassistProxyFactory的getInvoker（）方法获取了代理类
                        Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass,
                                registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
                        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

                        //1、Protocol的适配器类Protocol$Adaptive，export方法
                        // 如果为远程服务暴露，根据URL中Protocol的类型为registry，会选择Protocol的实现类RegistryProtocol
                        // 如果为本地服务暴露，根据URL中rotocol的类型为injvm，会选择Protocol的实现类InjvmProtocol
                        //2、由于Dubbo SPI的扩展点使用了Wrapper自动增强，这里就使用了ProtocolFilterWrapper、ProtocolListenerWrapper、
                        // QosProtocolWrapper对其进行了增强，所以要一层层调用才会调用到RegistryProtocol的export方法
                        Exporter<?> exporter = PROTOCOL.export(wrapperInvoker);
                        exporters.add(exporter);
                    }
                } else {
                    if (logger.isInfoEnabled()) {
                        logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                    }
                    //5.2.2、直连方式
                    Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, url);
                    DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

                    Exporter<?> exporter = PROTOCOL.export(wrapperInvoker);
                    exporters.add(exporter);
                }
                //5.2.3、元数据存储
                //如果使用MetadataReportConfig设置了元数据存储信息，将元数据保存到指定配置中心
                MetadataUtils.publishServiceDefinition(url);
            }
        }
        this.urls.add(url);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * always export injvm
     */
    private void exportLocal(URL url) {
        //创建一个新的URL对象，并且设置协议为injvm，然后设置Host为127.0.0.1，端口为0
        URL local = URLBuilder.from(url)
                .setProtocol(LOCAL_PROTOCOL)
                .setHost(LOCALHOST_VALUE)
                .setPort(0)
                .build();
        //导出服务
        //1、调用proxyFactory.getInvoker获取代理的对象，其中proxyFactory也是适配器类，默认proxyFactory扩展实现是JavassistProxyFactory，所以
        //这里调用JavassistProxyFactory的getInvoker方法来获取代理服务实现类的invoker类
        //2、然后，调用protocol.export导出服务，其中的protocol也是扩展接口，这里与远程服务暴露不同之处在于协议类型为injvm，所以会加载扩展实现类InjvmProtocol
        //InjvmProtocol也使用Wrapper类被增强了，并且在ProtocolFilterWrapper内也建立了Filter链
        Exporter<?> exporter = PROTOCOL.export(
                PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local));
        //保存导出服务到列表
        exporters.add(exporter);
        logger.info("Export dubbo service " + interfaceClass.getName() + " to local registry url : " + local);
    }

    /**
     * Determine if it is injvm
     *
     * @return
     */
    private boolean isOnlyInJvm() {
        return getProtocols().size() == 1
                && LOCAL_PROTOCOL.equalsIgnoreCase(getProtocols().get(0).getName());
    }


    /**
     * Register & bind IP address for service provider, can be configured separately.
     * Configuration priority: environment variables -> java system properties -> host property in config file ->
     * /etc/hosts -> default network address -> first available network address
     *
     * @param protocolConfig
     * @param registryURLs
     * @param map
     * @return
     */
    private String findConfigedHosts(ProtocolConfig protocolConfig,
                                     List<URL> registryURLs,
                                     Map<String, String> map) {
        boolean anyhost = false;

        String hostToBind = getValueFromConfig(protocolConfig, DUBBO_IP_TO_BIND);
        if (hostToBind != null && hostToBind.length() > 0 && isInvalidLocalHost(hostToBind)) {
            throw new IllegalArgumentException("Specified invalid bind ip from property:" + DUBBO_IP_TO_BIND + ", value:" + hostToBind);
        }

        // if bind ip is not found in environment, keep looking up
        if (StringUtils.isEmpty(hostToBind)) {
            hostToBind = protocolConfig.getHost();
            if (provider != null && StringUtils.isEmpty(hostToBind)) {
                hostToBind = provider.getHost();
            }
            if (isInvalidLocalHost(hostToBind)) {
                anyhost = true;
                logger.info("No valid ip found from environment, try to get local host.");
                hostToBind = getLocalHost();
            }
        }

        map.put(BIND_IP_KEY, hostToBind);

        // registry ip is not used for bind ip by default
        String hostToRegistry = getValueFromConfig(protocolConfig, DUBBO_IP_TO_REGISTRY);
        if (hostToRegistry != null && hostToRegistry.length() > 0 && isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException(
                    "Specified invalid registry ip from property:" + DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        } else if (StringUtils.isEmpty(hostToRegistry)) {
            // bind ip is used as registry ip by default
            hostToRegistry = hostToBind;
        }

        map.put(ANYHOST_KEY, String.valueOf(anyhost));

        return hostToRegistry;
    }


    /**
     * Register port and bind port for the provider, can be configured separately
     * Configuration priority: environment variable -> java system properties -> port property in protocol config file
     * -> protocol default port
     *
     * @param protocolConfig
     * @param name
     * @param protocolConfigNum
     * @return
     */
    private Integer findConfigedPorts(ProtocolConfig protocolConfig,
                                      String name,
                                      Map<String, String> map, int protocolConfigNum) {
        Integer portToBind = null;

        // parse bind port from environment
        String port = getValueFromConfig(protocolConfig, DUBBO_PORT_TO_BIND);
        portToBind = parsePort(port);

        // if there's no bind port found from environment, keep looking up.
        if (portToBind == null) {
            portToBind = protocolConfig.getPort();
            if (provider != null && (portToBind == null || portToBind == 0)) {
                portToBind = provider.getPort();
            }
            final int defaultPort = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(name).getDefaultPort();
            if (portToBind == null || portToBind == 0) {
                portToBind = defaultPort;
            }
            if (portToBind <= 0) {
                portToBind = getRandomPort(name);
                if (portToBind == null || portToBind < 0) {
                    portToBind = getAvailablePort(defaultPort);
                    putRandomPort(name, portToBind);
                }
            }
        }

        // registry port, not used as bind port by default
        String key = DUBBO_PORT_TO_REGISTRY;
        if (protocolConfigNum > 1) {
            key = getProtocolConfigId(protocolConfig).toUpperCase() + "_" + key;
        }
        String portToRegistryStr = getValueFromConfig(protocolConfig, key);
        Integer portToRegistry = parsePort(portToRegistryStr);
        if (portToRegistry != null) {
            portToBind = portToRegistry;
        }

        // save bind port, used as url's key later
        map.put(BIND_PORT_KEY, String.valueOf(portToBind));

        return portToBind;
    }


    private String getProtocolConfigId(ProtocolConfig config) {
        return Optional.ofNullable(config.getId()).orElse(DUBBO);
    }

    private Integer parsePort(String configPort) {
        Integer port = null;
        if (configPort != null && configPort.length() > 0) {
            try {
                Integer intPort = Integer.parseInt(configPort);
                if (isInvalidPort(intPort)) {
                    throw new IllegalArgumentException("Specified invalid port from env value:" + configPort);
                }
                port = intPort;
            } catch (Exception e) {
                throw new IllegalArgumentException("Specified invalid port from env value:" + configPort);
            }
        }
        return port;
    }

    private String getValueFromConfig(ProtocolConfig protocolConfig, String key) {
        String protocolPrefix = protocolConfig.getName().toUpperCase() + "_";
        String value = ConfigUtils.getSystemProperty(protocolPrefix + key);
        if (StringUtils.isEmpty(value)) {
            value = ConfigUtils.getSystemProperty(key);
        }
        return value;
    }

    private Integer getRandomPort(String protocol) {
        protocol = protocol.toLowerCase();
        return RANDOM_PORT_MAP.getOrDefault(protocol, Integer.MIN_VALUE);
    }

    private void putRandomPort(String protocol, Integer port) {
        protocol = protocol.toLowerCase();
        if (!RANDOM_PORT_MAP.containsKey(protocol)) {
            RANDOM_PORT_MAP.put(protocol, port);
            logger.warn("Use random available port(" + port + ") for protocol " + protocol);
        }
    }

    private void postProcessConfig() {
        List<ConfigPostProcessor> configPostProcessors = ExtensionLoader.getExtensionLoader(ConfigPostProcessor.class)
                .getActivateExtension(URL.valueOf(CONFIG_POST_PROCESSOR_PROTOCOL), (String[]) null);
        configPostProcessors.forEach(component -> component.postProcessServiceConfig(this));
    }

    /**
     * Dispatch an {@link Event event}
     *
     * @param event an {@link Event event}
     * @since 2.7.5
     */
    private void dispatch(Event event) {
        EventDispatcher.getDefaultExtension().dispatch(event);
    }

    public DubboBootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(DubboBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public String getServiceName() {

        if (!StringUtils.isBlank(serviceName)) {
            return serviceName;
        }
        String generateVersion = version;
        String generateGroup = group;

        if (StringUtils.isBlank(version) && provider != null) {
            generateVersion = provider.getVersion();
        }

        if (StringUtils.isBlank(group) && provider != null) {
            generateGroup = provider.getGroup();
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ServiceBean:");

        if (!StringUtils.isBlank(generateGroup)) {
            stringBuilder.append(generateGroup);
        }

        stringBuilder.append("/").append(interfaceName);

        if (!StringUtils.isBlank(generateVersion)) {
            stringBuilder.append(":").append(generateVersion);
        }

        serviceName = stringBuilder.toString();
        return serviceName;
    }
}
