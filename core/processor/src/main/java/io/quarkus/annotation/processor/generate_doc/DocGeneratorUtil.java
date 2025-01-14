package io.quarkus.annotation.processor.generate_doc;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.type.DeclaredType;

import io.quarkus.annotation.processor.Constants;

public class DocGeneratorUtil {
    static final String VERTX_JAVA_DOC_SITE = "https://vertx.io/docs/apidocs/";
    static final String OFFICIAL_JAVA_DOC_BASE_LINK = "https://docs.oracle.com/javase/8/docs/api/";
    static final String AGROAL_API_JAVA_DOC_SITE = "https://jar-download.com/javaDoc/io.agroal/agroal-api/1.5/index.html?";

    private static final Map<String, String> JAVA_PRIMITIVE_WRAPPERS = new HashMap<>();
    private static final Map<String, String> PRIMITIVE_DEFAULT_VALUES = new HashMap<>();
    private static final Map<String, String> EXTENSION_JAVA_DOC_LINK = new HashMap<>();
    private static Pattern PACKAGE_PATTERN = Pattern.compile("^(\\w+)\\.(\\w+)\\..*$");

    static {
        PRIMITIVE_DEFAULT_VALUES.put("int", "0");
        PRIMITIVE_DEFAULT_VALUES.put("byte", "0");
        PRIMITIVE_DEFAULT_VALUES.put("char", "");
        PRIMITIVE_DEFAULT_VALUES.put("short", "0");
        PRIMITIVE_DEFAULT_VALUES.put("long", "0l");
        PRIMITIVE_DEFAULT_VALUES.put("float", "0f");
        PRIMITIVE_DEFAULT_VALUES.put("double", "0d");
        PRIMITIVE_DEFAULT_VALUES.put("boolean", "false");

        JAVA_PRIMITIVE_WRAPPERS.put("java.lang.Character", "char");
        JAVA_PRIMITIVE_WRAPPERS.put("java.lang.Boolean", "boolean");
        JAVA_PRIMITIVE_WRAPPERS.put("java.lang.Byte", "byte");
        JAVA_PRIMITIVE_WRAPPERS.put("java.lang.Short", "short");
        JAVA_PRIMITIVE_WRAPPERS.put("java.lang.Integer", "int");
        JAVA_PRIMITIVE_WRAPPERS.put("java.lang.Long", "long");
        JAVA_PRIMITIVE_WRAPPERS.put("java.lang.Float", "float");
        JAVA_PRIMITIVE_WRAPPERS.put("java.lang.Double", "double");

        EXTENSION_JAVA_DOC_LINK.put("io.vertx.", VERTX_JAVA_DOC_SITE);
        EXTENSION_JAVA_DOC_LINK.put("io.agroal.", AGROAL_API_JAVA_DOC_SITE);
    }

    /**
     * Retrieve a default value of a primitive type.
     * If type is not a primitive, returns false
     *
     * @param primitiveType
     * @return
     */
    static String getPrimitiveDefaultValue(String primitiveType) {
        return PRIMITIVE_DEFAULT_VALUES.get(primitiveType);
    }

    /**
     * Replaces Java primitive wrapper types with primitive types
     */
    static String unbox(String type) {
        String mapping = JAVA_PRIMITIVE_WRAPPERS.get(type);
        return mapping == null ? type : mapping;
    }

    /**
     * Get javadoc link of a given type value
     */
    static String getJavaDocSiteLink(String type) {
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(type);

        if (!packageMatcher.find()) {
            return Constants.EMPTY;
        }

        if (JAVA_PRIMITIVE_WRAPPERS.containsKey(type)) {
            return Constants.EMPTY;
        }

        if ("java".equals(packageMatcher.group(1))) {
            return OFFICIAL_JAVA_DOC_BASE_LINK + getJavaDocLinkForType(type);
        }

        String basePkgName = packageMatcher.group(1) + "." + packageMatcher.group(2) + ".";
        String javaDocBaseUrl = EXTENSION_JAVA_DOC_LINK.get(basePkgName);

        if (javaDocBaseUrl != null) {
            return javaDocBaseUrl + getJavaDocLinkForType(type);
        }

        return Constants.EMPTY;
    }

    private static String getJavaDocLinkForType(String type) {
        int indexOfFirstUpperCase = 0;

        for (int index = 0; index < type.length(); index++) {
            char charAt = type.charAt(index);
            if (charAt >= 'A' && charAt <= 'Z') {
                indexOfFirstUpperCase = index;
                break;
            }
        }

        final String base = type.substring(0, indexOfFirstUpperCase).replace('.', '/');
        final String html = type.substring(indexOfFirstUpperCase).replace('$', '.') + ".html";

        return base + html;
    }

    /**
     * Retrieve enclosed type from known optional types
     */
    static String getKnownGenericType(DeclaredType declaredType) {
        return Constants.ALIASED_TYPES.get(declaredType.toString());
    }

    static Iterator<String> camelHumpsIterator(String str) {
        return new Iterator<String>() {
            int idx;

            @Override
            public boolean hasNext() {
                return idx < str.length();
            }

            @Override
            public String next() {
                if (idx == str.length())
                    throw new NoSuchElementException();
                // known mixed-case rule-breakers
                if (str.startsWith("JBoss", idx)) {
                    idx += 5;
                    return "JBoss";
                }
                final int start = idx;
                int c = str.codePointAt(idx);
                if (Character.isUpperCase(c)) {
                    // an uppercase-starting word
                    idx = str.offsetByCodePoints(idx, 1);
                    if (idx < str.length()) {
                        c = str.codePointAt(idx);
                        if (Character.isUpperCase(c)) {
                            // all-caps word; need one look-ahead
                            int nextIdx = str.offsetByCodePoints(idx, 1);
                            while (nextIdx < str.length()) {
                                c = str.codePointAt(nextIdx);
                                if (Character.isLowerCase(c)) {
                                    // ended at idx
                                    return str.substring(start, idx);
                                }
                                idx = nextIdx;
                                nextIdx = str.offsetByCodePoints(idx, 1);
                            }
                            // consumed the whole remainder, update idx to length
                            idx = str.length();
                            return str.substring(start);
                        } else {
                            // initial caps, trailing lowercase
                            idx = str.offsetByCodePoints(idx, 1);
                            while (idx < str.length()) {
                                c = str.codePointAt(idx);
                                if (Character.isUpperCase(c)) {
                                    // end
                                    return str.substring(start, idx);
                                }
                                idx = str.offsetByCodePoints(idx, 1);
                            }
                            // consumed the whole remainder
                            return str.substring(start);
                        }
                    } else {
                        // one-letter word
                        return str.substring(start);
                    }
                } else {
                    // a lowercase-starting word
                    idx = str.offsetByCodePoints(idx, 1);
                    while (idx < str.length()) {
                        c = str.codePointAt(idx);
                        if (Character.isUpperCase(c)) {
                            // end
                            return str.substring(start, idx);
                        }
                        idx = str.offsetByCodePoints(idx, 1);
                    }
                    // consumed the whole remainder
                    return str.substring(start);
                }
            }
        };
    }

    static Iterator<String> lowerCase(Iterator<String> orig) {
        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return orig.hasNext();
            }

            @Override
            public String next() {
                return orig.next().toLowerCase(Locale.ROOT);
            }
        };
    }

    static String join(String delim, Iterator<String> it) {
        final StringBuilder b = new StringBuilder();
        if (it.hasNext()) {
            b.append(it.next());
            while (it.hasNext()) {
                b.append(delim);
                b.append(it.next());
            }
        }
        return b.toString();
    }

    static String hyphenate(String orig) {
        return join("-", lowerCase(camelHumpsIterator(orig)));
    }

    static String hyphenateEnumValue(String orig) {
        return orig.replace('_', '-').toLowerCase(Locale.ROOT);
    }

    static String joinAcceptedValues(List<String> acceptedValues) {
        if (acceptedValues == null || acceptedValues.isEmpty()) {
            return "";
        }

        return acceptedValues.stream().collect(Collectors.joining("`, `", "`", "`"));
    }

    static String getTypeFormatInformationNote(ConfigDocKey configDocKey) {
        if (configDocKey.getType().equals(Duration.class.getName())) {
            return Constants.DURATION_INFORMATION;
        } else if (configDocKey.getType().equals(Constants.MEMORY_SIZE_TYPE)) {
            return Constants.MEMORY_SIZE_INFORMATION;
        }

        return Constants.EMPTY;
    }

    static boolean hasDurationInformationNote(ConfigDocKey configDocKey) {
        return configDocKey.hasType() && configDocKey.getType().equals(Duration.class.getName());
    }

    static boolean hasMemoryInformationNote(ConfigDocKey configDocKey) {
        return configDocKey.hasType() && configDocKey.getType().equals(Constants.MEMORY_SIZE_TYPE);
    }

    /**
     * Guess extension name from given configuration root file
     */
    public static String computeExtensionDocFileName(String configRoot) {
        final Matcher matcher = Constants.PKG_PATTERN.matcher(configRoot);
        if (!matcher.find()) {
            return configRoot + Constants.ADOC_EXTENSION;
        }

        String extensionName = matcher.group(1);
        final String subgroup = matcher.group(2);
        final StringBuilder key = new StringBuilder(Constants.QUARKUS);
        key.append(Constants.DASH);

        if (Constants.DEPLOYMENT.equals(extensionName) || Constants.RUNTIME.equals(extensionName)) {
            final String configClass = configRoot.substring(configRoot.lastIndexOf(Constants.DOT) + 1);
            extensionName = hyphenate(configClass);
            key.append(Constants.CORE);
            key.append(extensionName);
        } else if (subgroup != null && !Constants.DEPLOYMENT.equals(subgroup)
                && !Constants.RUNTIME.equals(subgroup) && !Constants.COMMON.equals(subgroup)
                && subgroup.matches(Constants.DIGIT_OR_LOWERCASE)) {
            key.append(extensionName);
            key.append(Constants.DASH);
            key.append(subgroup);

            final String qualifier = matcher.group(3);
            if (qualifier != null && !Constants.DEPLOYMENT.equals(qualifier)
                    && !Constants.RUNTIME.equals(qualifier) && !Constants.COMMON.equals(qualifier)
                    && qualifier.matches(Constants.DIGIT_OR_LOWERCASE)) {
                key.append(Constants.DASH);
                key.append(qualifier);
            }
        } else {
            key.append(extensionName);
        }

        key.append(Constants.ADOC_EXTENSION);
        return key.toString();
    }

    static void appendConfigItemsIntoExistingOnes(List<ConfigDocItem> existingConfigItems,
            List<ConfigDocItem> configDocItems) {
        for (ConfigDocItem configDocItem : configDocItems) {
            if (configDocItem.isConfigKey()) {
                existingConfigItems.add(configDocItem);
            } else {
                ConfigDocSection configDocSection = configDocItem.getConfigDocSection();
                boolean configSectionMerged = mergeSectionIntoPreviousExistingConfigItems(configDocSection,
                        existingConfigItems);
                if (!configSectionMerged) {
                    existingConfigItems.add(configDocItem);
                }
            }
        }
    }

    /**
     * returns true if section is merged into one of the existing config items, false otherwise
     */
    private static boolean mergeSectionIntoPreviousExistingConfigItems(ConfigDocSection section,
            List<ConfigDocItem> configDocItems) {
        for (ConfigDocItem configDocItem : configDocItems) {
            if (configDocItem.isConfigKey()) {
                continue;
            }

            ConfigDocSection configDocSection = configDocItem.getConfigDocSection();
            if (configDocSection.equals(section)) {
                configDocSection.addConfigDocItems(section.getConfigDocItems());
                return true;
            } else {
                boolean configSectionMerged = mergeSectionIntoPreviousExistingConfigItems(section,
                        configDocSection.getConfigDocItems());
                if (configSectionMerged) {
                    return true;
                }
            }
        }

        return false;
    }
}
