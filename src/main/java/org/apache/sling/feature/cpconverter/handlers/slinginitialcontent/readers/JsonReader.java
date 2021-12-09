/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.readers;

import org.apache.jackrabbit.commons.SimpleValueFactory;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionDefinition;
import org.apache.sling.feature.cpconverter.accesscontrol.RestrictionProvider;
import org.apache.sling.jcr.contentloader.ContentCreator;

import javax.jcr.*;
import javax.json.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class  JsonReader extends org.apache.sling.jcr.contentloader.internal.readers.JsonReader {
    
    private static final String SECURITY_PRINCIPLES = "security:principals";
    private static final String SECURITY_ACL = "security:acl";
 
    private ValueFactory factory = new SimpleValueFactory();


    protected boolean handleSecurity(String n, Object o, ContentCreator contentCreator) throws RepositoryException {
        if (SECURITY_PRINCIPLES.equals(n)) {
            this.createPrincipals(o, contentCreator);
        } else if (SECURITY_ACL.equals(n)) {
            this.createAclOverride(o, contentCreator);
        } else {
            return false;
        }
        return true;
        
    }

    private void createAclOverride(Object obj, ContentCreator contentCreator) throws RepositoryException {
        if (obj instanceof JsonObject) {
            // single ace
            createAceOverride((JsonObject) obj, contentCreator);
        } else if (obj instanceof JsonArray) {
            // array of aces
            JsonArray jsonArray = (JsonArray) obj;
            for (int i = 0; i < jsonArray.size(); i++) {
                Object object = jsonArray.get(i);
                if (object instanceof JsonObject) {
                    createAceOverride((JsonObject) object, contentCreator);
                } else {
                    throw new JsonException("Unexpected data type in acl array: " + object.getClass().getName());
                }
            }
        }
    }
    
    
    
    /**
     * Create or update an access control entry
     */
    private void createAceOverride(JsonObject ace, ContentCreator contentCreator) throws RepositoryException {

        String principalID = ace.getString("principal");

        String [] grantedPrivileges = null;
        JsonArray granted = (JsonArray) ace.get("granted");
        if (granted != null) {
            grantedPrivileges = new String[granted.size()];
            for (int a=0; a < grantedPrivileges.length; a++) {
                grantedPrivileges[a] = granted.getString(a);
            }
        }

        String [] deniedPrivileges = null;
        JsonArray denied = (JsonArray) ace.get("denied");
        if (denied != null) {
            deniedPrivileges = new String[denied.size()];
            for (int a=0; a < deniedPrivileges.length; a++) {
                deniedPrivileges[a] = denied.getString(a);
            }
        }

        String order = ace.getString("order", null);

        Map<String, Value> restrictionsMap = new HashMap<>();
        Map<String, Value[]> mvRestrictionsMap = new HashMap<>();
        Set<String> removedRestrictionNames = new HashSet<>();
        
        if(ace.containsKey("restrictions")){
            JsonObject restrictions = (JsonObject) ace.get("restrictions");

            Set<String> keySet = restrictions.keySet();
            for (String rname : keySet) {
                if (rname.endsWith("@Delete")) {
                    //add the key to the 'remove' set.  the value doesn't matter and is ignored.
                    String rname2 = rname.substring(9, rname.length() - 7);
                    removedRestrictionNames.add(rname2);
                } else {
                    RestrictionDefinition rd = RestrictionProvider.get(rname);
                    if (rd == null) {
                        //illegal restriction name?
                        throw new JsonException("Invalid or not supported restriction name was supplied: " + rname);
                    }

                    boolean multival = rd.getRequiredType().isArray();

                    int restrictionType = PropertyType.STRING;
                    //read the requested restriction value and apply it
                    JsonValue jsonValue = restrictions.get(rname);

                    if (multival) {
                        if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
                            JsonArray jsonArray = (JsonArray) jsonValue;
                            int size = jsonArray.size();
                            Value[] values = new Value[size];
                            for (int i = 0; i < size; i++) {
                                values[i] = toValueOverride(factory, jsonArray.get(i), restrictionType);
                            }
                            mvRestrictionsMap.put(rname, values);
                        } else {
                            Value v = toValueOverride(factory, jsonValue, restrictionType);
                            mvRestrictionsMap.put(rname, new Value[]{v});
                        }
                    } else {
                        if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
                            JsonArray jsonArray = (JsonArray) jsonValue;
                            int size = jsonArray.size();
                            if (size == 1) {
                                Value v = toValueOverride(factory, jsonArray.get(0), restrictionType);
                                restrictionsMap.put(rname, v);
                            } else if (size > 1) {
                                throw new JsonException("Unexpected multi value array data found for single-value restriction value for name: " + rname);
                            }
                        } else {
                            Value v = toValueOverride(factory, jsonValue, restrictionType);
                            restrictionsMap.put(rname, v);
                        }
                    }
                }
            } 
        }
        

        //do the work.
        if (restrictionsMap == null && mvRestrictionsMap == null && removedRestrictionNames == null) {
            contentCreator.createAce(principalID, grantedPrivileges, deniedPrivileges, order);
        } else {
            contentCreator.createAce(principalID, grantedPrivileges, deniedPrivileges, order, restrictionsMap, mvRestrictionsMap,
                    removedRestrictionNames == null ? null : removedRestrictionNames);
        }
    }

    /**
     * Attempt to convert the JsonValue to the equivalent JCR Value object
     *
     * @param factory the JCR value factory
     * @param jsonValue the JSON value to convert
     * @param restrictionType a hint for the expected property type of the value
     * @return the Value if converted or null otherwise
     * @throws ValueFormatException
     */
    private Value toValueOverride(ValueFactory factory, JsonValue jsonValue, int restrictionType) throws ValueFormatException {
        Value value = null;
        JsonValue.ValueType valueType = jsonValue.getValueType();
        switch (valueType) {
            case TRUE:
                value = factory.createValue(true);
                break;
            case FALSE:
                value = factory.createValue(false);
                break;
            case NUMBER:
                JsonNumber jsonNumber = (JsonNumber)jsonValue;
                if (jsonNumber.isIntegral()) {
                    value = factory.createValue(jsonNumber.longValue());
                } else {
                    value = factory.createValue(jsonNumber.doubleValue());
                }
                break;
            case STRING:
                value = factory.createValue(((JsonString)jsonValue).getString(), restrictionType);
                break;
            case NULL:
                value = null;
                break;
            case ARRAY:
            case OBJECT:
            default:
                //illegal JSON?
                break;
        }

        return value;
    }

}
