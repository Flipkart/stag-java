package com.vimeo.stag;


import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

public final class Types {

    private Types() {}

    public static WildcardType getWildcardType(Type[] upperBounds, Type[] lowerBounds) {
        return new WildcardTypeImpl(upperBounds, lowerBounds);
    }

    private static final class WildcardTypeImpl implements WildcardType {

        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        public WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
            this.upperBounds = upperBounds;
            this.lowerBounds = lowerBounds;
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds;
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds;
        }
    }
}
