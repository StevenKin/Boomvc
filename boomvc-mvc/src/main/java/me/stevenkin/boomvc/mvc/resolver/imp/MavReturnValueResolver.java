package me.stevenkin.boomvc.mvc.resolver.imp;

import me.stevenkin.boomvc.http.HttpRequest;
import me.stevenkin.boomvc.http.HttpResponse;
import me.stevenkin.boomvc.mvc.annotation.Restful;
import me.stevenkin.boomvc.mvc.resolver.ReturnValueResolver;
import me.stevenkin.boomvc.mvc.view.ModelAndView;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class MavReturnValueResolver implements ReturnValueResolver {
    @Override
    public boolean support(Type returnType, Method method) {
        return method.getAnnotation(Restful.class) == null && ModelAndView.class.equals(returnType);
    }

    @Override
    public ModelAndView resolve(Object returnValue, Method method, Type returnType, HttpRequest request, HttpResponse response) throws Exception {
        ModelAndView modelAndView = (ModelAndView) returnValue;
        modelAndView.mergeAttributes(request.attributes());
        return modelAndView;
    }
}
