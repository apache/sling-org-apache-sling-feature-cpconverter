package org.apache.sling.feature.cpconverter.accesscontrol;


import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionDefinition;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionDefinitionImpl;

import java.util.HashMap;
import java.util.Map;

import static org.apache.jackrabbit.oak.spi.security.authorization.accesscontrol.AccessControlConstants.*;
import static org.apache.jackrabbit.oak.spi.security.authorization.accesscontrol.AccessControlConstants.REP_ITEM_NAMES;

public class RestrictionProvider {
    
    static final Map<String, RestrictionDefinition> RESTRICTION_DEFINITION_MAP = new HashMap<>();

    static {
        RESTRICTION_DEFINITION_MAP.put(REP_GLOB, new RestrictionDefinitionImpl(REP_GLOB, Type.STRING, false));
        RESTRICTION_DEFINITION_MAP.put(REP_NT_NAMES, new RestrictionDefinitionImpl(REP_NT_NAMES, Type.NAMES, false));
        RESTRICTION_DEFINITION_MAP.put(REP_PREFIXES, new RestrictionDefinitionImpl(REP_PREFIXES, Type.STRINGS, false));
        RESTRICTION_DEFINITION_MAP.put(REP_ITEM_NAMES, new RestrictionDefinitionImpl(REP_ITEM_NAMES, Type.NAMES, false));
        RESTRICTION_DEFINITION_MAP.put("rep:current", new RestrictionDefinitionImpl("rep:current", Type.STRING, false));
    }

    public static RestrictionDefinition get(String key){
        return RESTRICTION_DEFINITION_MAP.get(key);
    }
}
