/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.management.internal.web.controllers.support;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.gemstone.gemfire.GemFireConfigException;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.distributed.DistributedMember;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.internal.ClassLoadUtil;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import com.gemstone.gemfire.internal.logging.InternalLogWriter;
import com.gemstone.gemfire.internal.logging.LogService;
import com.gemstone.gemfire.management.ManagementService;
import com.gemstone.gemfire.management.internal.SystemManagementService;
import com.gemstone.gemfire.management.internal.security.CLIOperationContext;
import com.gemstone.gemfire.management.internal.security.MBeanServerWrapper;
import com.gemstone.gemfire.management.internal.security.ResourceConstants;
import com.gemstone.gemfire.security.AccessControl;
import com.gemstone.gemfire.security.AuthenticationFailedException;
import com.gemstone.gemfire.security.AuthenticationRequiredException;
import com.gemstone.gemfire.security.Authenticator;

import org.apache.logging.log4j.Logger;

/**
 * The GetEnvironmentHandlerInterceptor class handles extracting Gfsh environment variables encoded in the HTTP request
 * message as request parameters.
 * <p/>
 * @author John Blum
 * @see javax.servlet.http.HttpServletRequest
 * @see javax.servlet.http.HttpServletResponse
 * @see org.springframework.web.servlet.handler.HandlerInterceptorAdapter
 * @since 8.0
 */
@SuppressWarnings("unused")
public class EnvironmentVariablesHandlerInterceptor extends HandlerInterceptorAdapter {

  private static final Logger logger = LogService.getLogger();
  
  private Cache cache;
  
  private Authenticator auth = null;
  
  
  public static final ThreadLocal<Properties> CREDENTIALS = new ThreadLocal<Properties>();
  
   
  private static final ThreadLocal<Map<String, String>> ENV = new ThreadLocal<Map<String, String>>() {
    @Override
    protected Map<String, String> initialValue() {
      return Collections.emptyMap();
    }
  };

  protected static final String ENVIRONMENT_VARIABLE_REQUEST_PARAMETER_PREFIX = "vf.gf.env.";
  
  protected static final String SECURITY_VARIABLE_REQUEST_HEADER_PREFIX = "security-";

  public static Map<String, String> getEnvironment() {
    return ENV.get();
  }

  @Override
  public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler)
    throws Exception
  {
    
    final Map<String, String> requestParameterValues = new HashMap<String, String>();

    for (Enumeration<String> requestParameters = request.getParameterNames(); requestParameters.hasMoreElements(); ) {
      final String requestParameter = requestParameters.nextElement();

      if (requestParameter.startsWith(ENVIRONMENT_VARIABLE_REQUEST_PARAMETER_PREFIX)) {
        requestParameterValues.put(requestParameter.substring(ENVIRONMENT_VARIABLE_REQUEST_PARAMETER_PREFIX.length()),
          request.getParameter(requestParameter));
      }
    }
    
 
    
    for (Enumeration<String> requestHeaders = request.getHeaderNames(); requestHeaders.hasMoreElements();) {

      final String requestHeader = requestHeaders.nextElement();

      if (requestHeader.startsWith(SECURITY_VARIABLE_REQUEST_HEADER_PREFIX)) {
        requestParameterValues.put(requestHeader, request.getHeader(requestHeader));
      }

    }
    
    securityCheck(requestParameterValues);
    
    ENV.set(requestParameterValues);

    return true;
  }
  

  
  protected void securityCheck(final Map<String, String> environment) {

    Properties credentials = new Properties();

    Iterator<Entry<String, String>> it = environment.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, String> entry = it.next();
      if (entry.getKey().startsWith(SECURITY_VARIABLE_REQUEST_HEADER_PREFIX)) {
        credentials.put(entry.getKey(), entry.getValue());
      }

    }
    GemFireCacheImpl instance = GemFireCacheImpl.getInstance();
    if(instance != null){
      SystemManagementService service = (SystemManagementService) ManagementService
          .getExistingManagementService(instance);
      service.getAuthManager().verifyCredentials(credentials);
      CREDENTIALS.set(credentials);
    }


  }

  

  @Override
  public void afterCompletion(final HttpServletRequest request,
                              final HttpServletResponse response,
                              final Object handler,
                              final Exception ex)
    throws Exception
  {
    afterConcurrentHandlingStarted(request, response, handler);
  }

  @Override
  public void afterConcurrentHandlingStarted(final HttpServletRequest request,
                                             final HttpServletResponse response,
                                             final Object handler)
    throws Exception
  {
    ENV.remove();
  }

}
