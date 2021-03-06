package com.taiji.boot.common.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReflectUtil;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Demo BeansUtils
 * 业务对象转换
 *
 * @author taiji
 * @date 2020/1/7 21:32
 */
public class TaiBeansUtils {

    private static final Logger logger = LoggerFactory.getLogger(TaiBeansUtils.class);

    /**
     * 功能描述: list对象转换
     * 将 list 按 function 的规则转换
     *
     * @param fs       待转换 list
     * @param function function 对象
     * @return : java.util.List<T>
     * @author : taiji
     * @date : 2020/1/7 21:34
     */
    public static <F, T> List<T> transformList(Collection<F> fs, Function<F, T> function) {
        if (CollectionUtils.isEmpty(fs)) {
            return Lists.newArrayList();
        }
        return fs.stream().map(function).collect(Collectors.toList());
    }

    /**
     * 功能描述: 将 list 转换成 map
     *
     * @param fs            待转换 list
     * @param keyFunction   键
     * @param valueFunction 值
     * @return : java.util.Map<K,V>
     * @author : taiji
     * @date : 2020/1/7 21:36
     */
    public static <F, K, V> Map<K, V> transformMap(Collection<F> fs, Function<? super F, K> keyFunction, Function<? super F, V> valueFunction) {
        HashMap<K, V> newHashMap = Maps.newHashMap();
        fs.forEach(x -> newHashMap.put(keyFunction.apply(x), valueFunction.apply(x)));
        return newHashMap;
    }

    /**
     * 功能描述: 将 list 转换成 set
     *
     * @param fs       待转换 list
     * @param function function 对象
     * @return : java.util.Set<T>
     * @author : ydy
     * @date : 2020/1/7 21:40
     */
    public static <F, T> Set<T> transformSet(Collection<F> fs, Function<F, T> function) {
        if (CollectionUtils.isEmpty(fs)) {
            return Sets.newHashSet();
        }
        return fs.stream().map(function).collect(Collectors.toSet());
    }

    /**
     * 功能描述: list 转 json
     *
     * @param fromCollection 1
     * @param toClass        2
     * @return : java.util.List<T>
     * @author : yangyihui
     * @date : 2020/1/7 0007 23:57
     */
    public static <F, T> List<T> transformListByJSON(Collection<F> fromCollection, Class<T> toClass) {
        return transformList(fromCollection, x -> transformByJSON(x, toClass));
    }

    /**
     * 功能描述: 对象转 json
     *
     * @param from    1
     * @param toClass 2
     * @return : T
     * @author : yangyihui
     * @date : 2020/1/7 0007 23:58
     */
    public static <F, T> T transformByJSON(F from, Class<T> toClass) {
        return JSON.parseObject(JSON.toJSONString(from), toClass);
    }

    /**
     * 确保Collection成员属性值正确拷贝
     *
     * @param source
     * @param target
     * @param <T>
     * @param <E>
     * @return
     */
    public static <T, E> List<E> copyProperties(List<T> source, Class<E> target) {
        if (CollectionUtils.isEmpty(source)) {
            return Lists.newArrayList();
        }
        List<E> result = Lists.newArrayListWithCapacity(source.size());
        source.forEach(x -> result.add(BeanUtil.toBean(x, target)));
        return result;
    }

    public static boolean allFieldIsNull(Object bean) {
        if (bean == null) {
            return true;
        }
        if (bean instanceof Integer) {
            return Objects.isNull(bean);
        }
        if (bean instanceof Float) {
            return Objects.isNull(bean);
        }
        if (bean instanceof Double) {
            return Objects.isNull(bean);
        }
        if (bean instanceof String) {
            return Objects.isNull(bean);
        }
        if (bean instanceof Map) {
            return MapUtils.isEmpty((Map) bean);
        }
        if (bean instanceof Set) {
            return CollectionUtils.isEmpty((Set) bean);
        }
        if (bean instanceof List) {
            return CollectionUtils.isEmpty((List) bean);
        }
        Map<String, Object> map = BeanUtil.beanToMap(bean);
        map.remove("class");
        map.remove("serialVersionUID");
        List<Object> values = map.values().stream().filter(x -> Objects.nonNull(x)).collect(Collectors.toList());
        return CollectionUtils.isEmpty(values);
    }

    /**
     * 功能描述: 检查bean中为空的字段
     * @param object bean对象
     * @return : java.lang.String 字段名
     * @author : YangDy
     * @date : 2020/11/19 11:43
     */
    public static String checkBeanFieldsNonNull(Object object) {
        if (null == object) {
            return null;
        }

        try {
            Field[] fields = ReflectUtil.getFields(object.getClass());
            if (ArrayUtil.isEmpty(fields)) {
                return null;
            }

            for (Field field : fields) {
                if (!field.getType().isPrimitive()) {
                    boolean origAccess = field.isAccessible();
                    field.setAccessible(true);
                    Object value = field.get(object);
                    field.setAccessible(origAccess);

                    if (null == value) {
                        return field.getName();
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public static void injectBeanPropByProps(Properties properties, Object object, String prefix) {
        if (null != properties && null != object && null != prefix) {
            try {
                Field[] fields = object.getClass().getDeclaredFields();
                if (ArrayUtil.isEmpty(fields)) {
                    return;
                }

                for (Field field : fields) {
                    if (Modifier.isFinal(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    String value = properties.getProperty(prefix + "." + field.getName());
                    if (null == value) {
                        continue;
                    }
                    value = value.trim();

                    boolean origAccess = field.isAccessible();
                    field.setAccessible(true);
                    field.set(object, convertValueToFieldType(field.getType(), value));
                    field.setAccessible(origAccess);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private static Object convertValueToFieldType(Class cls, String value) {
        if (null == cls || null == value) {
            return null;
        }

        if (cls.equals(boolean.class) || cls.equals(Boolean.class)) {
            return Boolean.valueOf(value);
        } else if (cls.equals(int.class) || cls.equals(Integer.class)) {
            return Integer.valueOf(value);
        } else if (cls.equals(long.class) || cls.equals(Long.class)) {
            return Long.valueOf(value);
        } else {
            return value;
        }
    }
}
