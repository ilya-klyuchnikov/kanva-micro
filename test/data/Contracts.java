package data;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Enumeration;
import java.util.List;

public class Contracts {
    static <E> boolean isEmptyList(List<E> list) {
      return list == null || list.isEmpty();
    }

    static <E> boolean isNotEmptyList(List<E> list) {
        return list != null && !list.isEmpty();
    }

    static boolean isTraversable(Object o) {
        if (o instanceof Iterable<?>) {
            return true;
        }
        if (o instanceof Enumeration<?>) {
            return true;
        }
        return false;
    }

    static boolean endsWith(final CharSequence str, final CharSequence suffix, final boolean ignoreCase) {
        if (str == null || suffix == null) {
            return str == null && suffix == null;
        }
        if (suffix.length() > str.length()) {
            return false;
        }
        return str.toString().endsWith(suffix.toString());
    }

    public static boolean isArrayType(final Type type) {
        return type instanceof GenericArrayType || type instanceof Class<?> && ((Class<?>) type).isArray();
    }

    public static boolean withCycle(int i, Object o) {
        while (i < 10) {
            i++;
        }
        if (o == null) {
            return false;
        } else {
            return i > 123;
        }

    }
}
