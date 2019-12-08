package org.hotswap.agent.plugin.mybatis.transformers;

import org.apache.ibatis.parsing.XPathParser;
import org.hotswap.agent.util.ReflectionHelper;

public class XPathParserCaller {

    public static String getSrcFileName(XPathParser parser) {
        return (String) ReflectionHelper.get(parser, MyBatisTransformers.SRC_FILE_NAME_FIELD);
    }

    public static boolean refreshDocument(XPathParser parser) {
        return (boolean) ReflectionHelper.invoke(parser, MyBatisTransformers.REFRESH_DOCUMENT_METHOD);
    }
}
