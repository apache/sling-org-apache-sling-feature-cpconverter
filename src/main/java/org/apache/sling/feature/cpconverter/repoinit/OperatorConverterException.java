/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.cpconverter.repoinit;

import org.apache.sling.feature.cpconverter.ConverterException;

/**
 * Runtime exception variant of the converter exception to pass it through
 * the operators/visitors
 */
public class OperatorConverterException extends RuntimeException {
    
    public OperatorConverterException(final ConverterException e) {
        super(e);
    }

    public ConverterException getConverterException() {
        return (ConverterException)this.getCause();
    }
}
