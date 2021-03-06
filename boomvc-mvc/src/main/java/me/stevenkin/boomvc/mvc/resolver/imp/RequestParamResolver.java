package me.stevenkin.boomvc.mvc.resolver.imp;

import me.stevenkin.boomvc.common.resolver.MethodParameter;
import me.stevenkin.boomvc.common.resolver.ParameterResolver;
import me.stevenkin.boomvc.http.HttpRequest;
import me.stevenkin.boomvc.http.HttpResponse;
import me.stevenkin.boomvc.ioc.annotation.Bean;

@Bean
public class RequestParamResolver implements ParameterResolver {
    @Override
    public boolean support(MethodParameter parameter) {
        return HttpRequest.class.isAssignableFrom((Class<?>) parameter.getParameterType());
    }

    @Override
    public Object resolve(MethodParameter parameter, HttpRequest request, HttpResponse response) throws Exception {
        return request;
    }
}
