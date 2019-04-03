/*
 * Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * This source was modified heavily from the Guava implementation of the same name:
 * https://github.com/google/guava/blob/master/guava/src/com/google/common/reflect/TypeToken.java
 *
 * Original source is Copyright (C) 2006 The Guava Authors
 * Licensed under the Apache License, Version 2.0 (the "License"):
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package software.amazon.awssdk.enhanced.dynamodb.model;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import software.amazon.awssdk.annotations.Immutable;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.annotations.ThreadSafe;
import software.amazon.awssdk.enhanced.dynamodb.converter.ItemAttributeValueConverter;
import software.amazon.awssdk.enhanced.dynamodb.internal.model.DefaultParameterizedType;
import software.amazon.awssdk.utils.ToString;
import software.amazon.awssdk.utils.Validate;

/**
 * A {@link Type} with generics.
 *
 * This is useful for being able to not only reflect on a specific class, but also any type parameters within that class.
 *
 * For example, this allows retrieving a {@code List.class} and {@code String.class} from a {@code TypeToken<List<String>>}.
 * This type-parameter information can then be used for more specific type conversion within a
 * {@link ItemAttributeValueConverter}.
 *
 * There are a few ways to create a {@link TypeToken}, depending on the type being represented:
 * <ul>
 *     <li>For non-parameterized types, use {@link TypeToken#from(Class)}, eg. {@code TypeToken.from(String.class)} for a
 *     {@code TypeToken<String>}.</li>
 *     <li>For parameterized lists, use {@link TypeToken#listOf(Class)}, eg. {@code TypeToken.listOf(String.class)} for a
 *     {@code TypeToken<List<String>>}.</li>
 *     <li>For parameterized maps, use {@link TypeToken#mapOf(Class, Class)},
 *     eg. {@code TypeToken.MapOf(String.class, Integer.class)} for a {@code TypeToken<Map<String, Integer>>}.</li>
 *     <li>For other parameterized types, you must create an anonymous subclass of this token, so that the type token can
 *     capture the type parameters using reflection. eg. {@code new TypeToken<Iterable<String>>()&#123;&#125;} (note the extra
 *     {}) for a {@code TypeToken<Iterable<String>>}.</li>
 * </ul>
 *
 * This source was modified heavily from the Guava implementation of the same name:
 * https://github.com/google/guava/blob/master/guava/src/com/google/common/reflect/TypeToken.java
 *
 * Original source is Copyright (C) 2006 The Guava Authors
 * Licensed under the Apache License, Version 2.0 (the "License"):
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Original Guava authors: Bob Lee, Sven Mawson, Ben Yu
 */
@SdkPublicApi
@ThreadSafe
@Immutable
public class TypeToken<T> {
    private final Class<T> rawClass;
    private final List<TypeToken<?>> rawClassParameters;

    /**
     * Create a type token, capturing the generic type arguments of the token as {@link Class}es.
     *
     * <b>This must be called from an anonymous subclass.</b> For example, </b>
     * {@code new TypeToken<Iterable<String>>()&#123;&#125;} (note the extra {}) for a {@code TypeToken<Iterable<String>>}.
     */
    protected TypeToken() {
        this(null);
    }

    private TypeToken(Type type) {
        if (type == null) {
            type = captureGenericTypeArguments();
        }

        this.rawClass = validateAndConvert(type);
        this.rawClassParameters = loadTypeParameters(type);
    }

    private static TypeToken<?> from(Type type) {
        return new TypeToken<>(validateIsSupportedType(type));
    }

    /**
     * Create a type token for the provided non-parameterized class.
     */
    public static <T> TypeToken<T> from(Class<T> type) {
        return new TypeToken<>(validateIsSupportedType(type));
    }

    /**
     * Create a type token for a list, with the provided value type class.
     */
    public static <T> TypeToken<List<T>> listOf(Class<T> valueType) {
        return new TypeToken<>(DefaultParameterizedType.parameterizedType(List.class, valueType));
    }

    /**
     * Create a type token for a map, with the provided key and value type classes.
     */
    public static <T, U> TypeToken<Map<T, U>> mapOf(Class<T> keyType, Class<U> valueType) {
        return new TypeToken<>(DefaultParameterizedType.parameterizedType(Map.class, keyType, valueType));
    }

    private static Type validateIsSupportedType(Type type) {
        Validate.validState(type != null, "Type must not be null.");
        Validate.validState(!(type instanceof GenericArrayType),
                            "Array type %s is not supported. Use java.util.List instead of arrays.", type);
        Validate.validState(!(type instanceof TypeVariable), "Type variable type %s is not supported.", type);
        Validate.validState(!(type instanceof WildcardType), "Wildcard type %s is not supported.", type);
        return type;
    }

    /**
     * Retrieve the {@link Class} object that this type token represents.
     *
     * Eg. For {@code TypeToken<String>}, this would return {@code String.class}.
     */
    public Class<T> rawClass() {
        return rawClass;
    }

    /**
     * Retrieve the {@link Class} objects of any type parameters for the class that this type token represents.
     *
     * Eg. For {@code TypeToken<List<String>>}, this would return {@code String.class}, and {@link #rawClass()} would
     * return {@code List.class}.
     *
     * If there are no type parameters, this will return an empty list.
     */
    public List<TypeToken<?>> rawClassParameters() {
        return rawClassParameters;
    }

    private Type captureGenericTypeArguments() {
        Type superclass = getClass().getGenericSuperclass();

        ParameterizedType parameterizedSuperclass =
                Validate.isInstanceOf(ParameterizedType.class, superclass, "%s isn't parameterized", superclass);

        return parameterizedSuperclass.getActualTypeArguments()[0];
    }

    private Class<T> validateAndConvert(Type type) {
        validateIsSupportedType(type);

        if (type instanceof Class) {
            return (Class<T>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return validateAndConvert(parameterizedType.getRawType());
        } else {
            throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    private List<TypeToken<?>> loadTypeParameters(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return Collections.emptyList();
        }

        ParameterizedType parameterizedType = (ParameterizedType) type;

        return Collections.unmodifiableList(
                Arrays.stream(parameterizedType.getActualTypeArguments())
                      .peek(t -> Validate.validState(t != null, "Invalid type argument."))
                      .map(TypeToken::from)
                      .collect(Collectors.toList()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypeToken)) {
            return false;
        }
        TypeToken<?> typeToken = (TypeToken<?>) o;
        return rawClass.equals(typeToken.rawClass) &&
               rawClassParameters.equals(typeToken.rawClassParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawClass, rawClassParameters);
    }

    @Override
    public String toString() {
        return ToString.builder("TypeToken")
                       .add("rawClass", rawClass)
                       .add("rawClassParameters", rawClassParameters)
                       .build();
    }
}
