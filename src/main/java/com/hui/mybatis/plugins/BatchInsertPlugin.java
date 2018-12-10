package com.hui.mybatis.plugins;

import org.mybatis.generator.api.CommentGenerator;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.OutputUtilities;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities;
import org.mybatis.generator.config.GeneratedKey;

import java.util.*;

/**
 * <b><code>BatchInsertPlugin</code></b>
 * <p/>
 * Description: 批量insert 和 insertSelective插件开发（Mybatis模式的时候）
 * <p/>
 * <b>Creation Time:</b> 2018/12/6 22:59.
 *
 * @author HuWeihui
 */
public class BatchInsertPlugin extends PluginAdapter {

    private final static String BATCH_INSERT = "batchInsert";

    private final static String BATCH_INSERTSELECTIVE = "batchInsertSelective";

    public boolean validate(List<String> list) {
        return true;
    }


    /**
     * java代码Mapper生成
     * @param interfaze
     * @param topLevelClass
     * @param introspectedTable
     * @return
     */
    @Override
    public boolean clientGenerated(Interface interfaze,
                                   TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        if (introspectedTable.getTargetRuntime() == IntrospectedTable.TargetRuntime.MYBATIS3) {
            //生成batchInsert 和 batchInsertSelective的java方法
            addBatchInsertMethod(interfaze, introspectedTable);
        }
        return super.clientGenerated(interfaze, topLevelClass,
                introspectedTable);
    }

    /**
     * sqlMapper生成
     * @param document
     * @param introspectedTable
     * @return
     */
    @Override
    public boolean sqlMapDocumentGenerated(Document document,
                                           IntrospectedTable introspectedTable) {
        if (introspectedTable.getTargetRuntime().equals(IntrospectedTable.TargetRuntime.MYBATIS3)) {
            //生成batchInsert 和 batchInsertSelective的java方法
            addBatchInsertSqlMap(document.getRootElement(), introspectedTable);
        }
        return super.sqlMapDocumentGenerated(document, introspectedTable);
    }

    /**
     * batchInsert和batchInsertSelective方法生成
     * @param parentElement
     * @param introspectedTable
     */
    private void addBatchInsertSqlMap(XmlElement parentElement, IntrospectedTable introspectedTable) {
        //1.Batchinsert
        XmlElement answer = new XmlElement("insert");

        answer.addAttribute(new Attribute("id", BATCH_INSERT));

        FullyQualifiedJavaType parameterType;
        parameterType = introspectedTable.getRules().calculateAllFieldsClass();

        answer.addAttribute(new Attribute("parameterType", parameterType.getFullyQualifiedName()));

        context.getCommentGenerator().addComment(answer);

        GeneratedKey gk = introspectedTable.getGeneratedKey();
        if (gk != null) {
            IntrospectedColumn introspectedColumn = introspectedTable
                    .getColumn(gk.getColumn());
            if (introspectedColumn != null) {
                if (gk.isJdbcStandard()) {
                    answer.addAttribute(new Attribute(
                            "useGeneratedKeys", "true"));
                    answer.addAttribute(new Attribute(
                            "keyProperty", introspectedColumn.getJavaProperty()));
                } else {
                    answer.addElement(getSelectKey(introspectedColumn, gk));
                }
            }
        }

        StringBuilder insertClause = new StringBuilder();
        StringBuilder valuesClause = new StringBuilder();

        insertClause.append("insert into ");
        insertClause.append(introspectedTable
                .getFullyQualifiedTableNameAtRuntime());
        insertClause.append(" (");

        valuesClause.append("values <foreach collection=\"list\" item=\"item\" index=\"index\" separator=\",\" > (");


        List<String> valuesClauses = new ArrayList<String>();
        Iterator<IntrospectedColumn> iter = introspectedTable.getAllColumns()
                .iterator();
        while (iter.hasNext()) {
            IntrospectedColumn introspectedColumn = iter.next();
            if (introspectedColumn.isIdentity()) {
                // cannot set values on identity fields
                continue;
            }

            insertClause.append(MyBatis3FormattingUtilities
                    .getEscapedColumnName(introspectedColumn));

            // 批量插入,如果是sequence字段,则插入不需要item.前缀
            if (introspectedColumn.isSequenceColumn()) {
                valuesClause.append(MyBatis3FormattingUtilities
                        .getParameterClause(introspectedColumn));
            } else {
                valuesClause.append(MyBatis3FormattingUtilities
                        .getParameterClause(introspectedColumn, "item."));
            }
            if (iter.hasNext()) {
                insertClause.append(", ");
                valuesClause.append(", ");
            }

            if (valuesClause.length() > 80) {
                answer.addElement(new TextElement(insertClause.toString()));
                insertClause.setLength(0);
                OutputUtilities.xmlIndent(insertClause, 1);

                valuesClauses.add(valuesClause.toString());
                valuesClause.setLength(0);
                OutputUtilities.xmlIndent(valuesClause, 1);
            }
        }

        insertClause.append(')');
        answer.addElement(new TextElement(insertClause.toString()));

        valuesClause.append(")</foreach>");
        valuesClauses.add(valuesClause.toString());

        for (String clause : valuesClauses) {
            answer.addElement(new TextElement(clause));
        }

        parentElement.addElement(answer);
    }

    protected XmlElement getSelectKey(IntrospectedColumn introspectedColumn,
                                      GeneratedKey generatedKey) {
        String identityColumnType = introspectedColumn
                .getFullyQualifiedJavaType().getFullyQualifiedName();

        XmlElement answer = new XmlElement("selectKey");
        answer.addAttribute(new Attribute("resultType", identityColumnType));
        answer.addAttribute(new Attribute(
                "keyProperty", introspectedColumn.getJavaProperty()));
        answer.addAttribute(new Attribute("order",
                generatedKey.getMyBatis3Order()));

        answer.addElement(new TextElement(generatedKey
                .getRuntimeSqlStatement()));

        return answer;
    }


    /**
     * java的Mapper类生成
     * @param interfaze
     * @param introspectedTable
     */
    private void addBatchInsertMethod(Interface interfaze, IntrospectedTable introspectedTable) {
        //获取实体类类型
        FullyQualifiedJavaType parameterType = introspectedTable.getRules().calculateAllFieldsClass();
        //Java List类型
        FullyQualifiedJavaType listType = FullyQualifiedJavaType.getNewListInstance();
        //@Param需要导入的类型
        FullyQualifiedJavaType paramType = new FullyQualifiedJavaType("org.apache.ibatis.annotations.Param");

        //导入
        Set<FullyQualifiedJavaType> importedTypes = new TreeSet<FullyQualifiedJavaType>();
        importedTypes.add(parameterType);
        importedTypes.add(listType);
        importedTypes.add(paramType);

        //List包住实体类
        FullyQualifiedJavaType listParameterType = FullyQualifiedJavaType.getNewListInstance();
        listParameterType.addTypeArgument(parameterType);


        //1.batchInsert
        Method insertMethod = new Method();
        insertMethod.setReturnType(FullyQualifiedJavaType.getIntInstance());
        insertMethod.setVisibility(JavaVisibility.PUBLIC);
        insertMethod.setName(BATCH_INSERT);
        insertMethod.addParameter(new Parameter(listParameterType, "recordList","@Param(\"recordList\")"));


        //2.batchInsertSelective
        Method insertSelectiveMethod = new Method();

        insertSelectiveMethod.setReturnType(FullyQualifiedJavaType.getIntInstance());
        insertSelectiveMethod.setVisibility(JavaVisibility.PUBLIC);
        insertSelectiveMethod.setName(BATCH_INSERTSELECTIVE);
        insertSelectiveMethod.addParameter(new Parameter(listParameterType,"recordList","@Param(\"recordList\")"));

        CommentGenerator commentGenerator = context.getCommentGenerator();

        commentGenerator.addGeneralMethodComment(insertMethod, introspectedTable);
        commentGenerator.addGeneralMethodComment(insertSelectiveMethod, introspectedTable);

        interfaze.addImportedTypes(importedTypes);
        interfaze.addMethod(insertMethod);
        interfaze.addMethod(insertSelectiveMethod);
    }
}
