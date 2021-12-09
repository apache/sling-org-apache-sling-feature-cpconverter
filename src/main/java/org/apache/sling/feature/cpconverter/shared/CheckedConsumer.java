package org.apache.sling.feature.cpconverter.shared;

import org.apache.sling.feature.cpconverter.ConverterException;

import java.io.IOException;

@FunctionalInterface
public interface CheckedConsumer<T> {
    void accept(T t) throws IOException, ConverterException;
}