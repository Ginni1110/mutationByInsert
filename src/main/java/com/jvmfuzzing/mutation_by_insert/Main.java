package com.jvmfuzzing.mutation_by_insert;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.utils.SourceRoot;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;

import com.jvmfuzzing.generator.VariableGenerater;
import com.jvmfuzzing.generator.VariableComponent;
import com.jvmfuzzing.apigetter.*;
import com.jvmfuzzing.mutation.*;

import java.util.*;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.sql.*;

public class Main {
    static VariableGenerater generator = new VariableGenerater();
    static ClassifyVariable classifier = new ClassifyVariable();
    static SelectDB s = new SelectDB();
    static CreateJavaFile createFile = new CreateJavaFile();
    static MutationbyInsert mutationbyInsert = new MutationbyInsert();
    Object lock = new Object();

    com.jvmfuzzing.apigetter.Mutator apiMutator = new com.jvmfuzzing.apigetter.Mutator();
    com.jvmfuzzing.mutation.Mutator varMutator = new com.jvmfuzzing.mutation.Mutator();
    ReadTxt read = new ReadTxt();
    int number = 100;
    
    // filepath 是变异的文件路径，seed_file是存储的种子文件路径
    public Statement Insert_Cov(List<CompilationUnit> Seed_compilations, String FilePath, String Seed_file)
            throws Exception {
        int file_num = 0;
        Map<String, List<CompilationUnit>> seed_Map = groupList(Seed_compilations);
        for (Map.Entry<String, List<CompilationUnit>> entry_com : seed_Map.entrySet()) {
            Insert(entry_com.getValue(), FilePath + "/" + file_num, Seed_file + "/" + file_num);
            file_num++;
        }
        System.out.println("total num :" + file_num);
        return null;
    }

    // 对文件进行分组
    public Map<String, List<CompilationUnit>> groupList(List<CompilationUnit> list) {

        if (list == null || list.size() == 0) {
            return new HashMap<String, List<CompilationUnit>>();
        }
        int listSize = list.size();
        int toIndex = number;
        Map<String, List<CompilationUnit>> map = new HashMap<String, List<CompilationUnit>>(); // 用map存起来新的分组后数据
        int keyToken = 0;

        for (int i = 0; i < list.size(); i += number) {
            if (i + number > listSize) { 
                toIndex = listSize - i;
            }
            List<CompilationUnit> newList = list.subList(i, i + toIndex);
            map.put("keyName" + keyToken, newList);
            keyToken++;
        }
        return map;
    }

    public Statement Insert(List<CompilationUnit> Seed_compilations, String filePath, String Seed_file)
            throws IOException, SQLException, ClassNotFoundException, InterruptedException {

        System.out.println("get for1");
        List<String> for_stmt = s.select();
        List<HashMap<String, String>> needsetVarName_list = s.getNeedfHashMaps();
        int numss = 0;
        int for_num = 0;
        List<CompilationUnit> apiMutator_resultList = new ArrayList<>();
        for (CompilationUnit cu : Seed_compilations) {
            String name = cu.getStorage().get().getPath().toString();
            String name_1 = cu.getStorage().get().getFileName();
            System.out.println(name);
            // List<CompilationUnit> varMutator_resultList = varMutator.callMutator(cu);
            List<ClassOrInterfaceDeclaration> c1 = cu.findAll(ClassOrInterfaceDeclaration.class);
            for (ClassOrInterfaceDeclaration c : c1) {
                List<MethodDeclaration> methods = c.getMethods();
                int size = methods.size() - 1;
                label1: for (int i = 0; i < size; i++) {
                    MethodDeclaration m = methods.get(i);
                    if (m.getBody().isPresent()) {
                        List<String> seed_content = new ArrayList<>();
                        seed_content.add(cu.toString());
                        createFile.createFile(Seed_file, name_1, seed_content.get(0));
                        BlockStmt methodBody = m.getBody().get();
                        if (methodBody == null)
                            continue;
                        Random random = new Random();
                        Statement test = null;
                        int num = random.nextInt(93);
                        HashMap<String, String> needsetVarName = new HashMap<>();
                        System.out.println("---------------------Start ---------------------");
                        test = StaticJavaParser.parseStatement(for_stmt.get(num));
                        needsetVarName = needsetVarName_list.get(num);
                        System.out.println("needsetVarName" + needsetVarName);
                        List<Statement> stmt_gen = new ArrayList<Statement>();

                        System.out.println("-----mutationbyInsert------");
                        stmt_gen = mutationbyInsert.setVarNameInInsertStmt(cu, m, test,needsetVarName, name);
                        System.out.println("CU:\n" + cu);
                        for_num++;

                        int stmt_gen_size = stmt_gen.size();
                        if (stmt_gen_size != 0) {
                            for (int index = 0; index < stmt_gen_size; index++) {
                                Statement stmt = stmt_gen.get(index);
                                addStmtBetweenStmt(methodBody, index, stmt);
                            }
                        }
                        if (test.toString().contains("TestFailure")) {
                            System.out.println("------Insert Import-----");
                            ImportDeclaration importDeclaration = StaticJavaParser
                                    .parseImport("import nsk.share.TestFailure;");
                            cu.addImport(importDeclaration);

                        }
                        // 对变异的结果生成文件
                        List<String> mutation_result = new ArrayList();
                        mutation_result.add(cu.toString());
                        CreateJavaFile.createFile(filePath, name_1, mutation_result.get(0));
                        System.out.println("-------------End-------------");
                        break label1;
                    }
                }
                System.out.println("I HERE");
            }
            System.out.println("---------------------" + numss + "-----------------------------");
            numss++;
        }

        return null;
    }

    public List<CompilationUnit> getCu(String path) throws IOException {
        Path pathToSource = Paths.get(path);
        SourceRoot sourceRoot = new SourceRoot(pathToSource);
        sourceRoot.tryToParse();
        List<CompilationUnit> compilations = FindJava(sourceRoot);
        return compilations;
    }

    public List Decommon(List list) {
        HashSet set_list_for_var = new HashSet(list);
        list.clear();
        list.addAll(set_list_for_var);
        return list;
    }

    public int generateChooseNum(int min, int max, boolean isLine) {
        System.out.println("min:" + min + " max:" + max);
        if ((min >= max) && isLine) {
            return 1;
        } else if ((min >= max) && !isLine) {
            return 0;
        }
        Random random = new Random();
        // +1是闭区间，不+1是左闭右开区间
        // return random.nextInt((max - min) + 1) + min;
        return random.nextInt(max - min) + min;
    }

    public void addStmtBetweenStmt(BlockStmt methodBody, int stmtIndex, Statement insertStmt) {
        methodBody.addStatement(stmtIndex, insertStmt);
    }

    public void setVarName(Statement visitStmt, String oldName, String newName) {
        visitStmt.findAll(NameExpr.class).forEach(n -> {
            if (n.getNameAsString().equals(oldName)) {
                n.setName(newName);
            }
        });
    }

    public List<Statement> callGenerateDec(CompilationUnit cu, String name, String type) throws NullPointerException {
        // 存放结果的变量
        List<Statement> varStmt = new ArrayList<Statement>();
        // 调用生成器
        System.out.println("---------------start callGenerateDec---------");
        List<VariableComponent> varComp = generator.generateDec(name, type);
        for (VariableComponent vc : varComp) {
            // 从自定义类中获取变量的各个组成部分，并组装成完整语句，例如：int a = 1;\n
            if (StaticJavaParser.parseStatement(
                    vc.getVarType() + " " + vc.getVarName() + " = " + vc.getVarValue().toString() + ";\n") == null)
                continue;
            varStmt.add(StaticJavaParser.parseStatement(
                    vc.getVarType() + " " + vc.getVarName() + " = " + vc.getVarValue().toString() + ";\n"));
            // 如果生成的参数引入了新的类型，在cu中进行检查和添加
            if (vc.getImportStmt().length() > 0) {
                Arrays.asList(vc.getImportStmt().split("\n")).forEach(s -> {
                    if (!cu.getImports().stream().anyMatch(i -> i.toString().equals(s))) {
                        cu.addImport(s);
                    }
                });
            }
        }
        return varStmt;
    }

    public List<Statement> setVarNameInInsertStmt(CompilationUnit cu, MethodDeclaration visitMethod,
            Statement insertStmt, HashMap<String, String> needfulParam) throws NullPointerException {
        List<Statement> newVarsDec = new ArrayList<Statement>();
        List<String> typeList = Arrays.asList("float", "long", "int", "char", "short", "byte");
        List<String> typeCast = Arrays.asList("double", "float", "long", "int", "char", "short");
        Map<String, String> commonType_differName = new HashMap<String, String>();
        Map<String, String> availableVar = classifier.getAvailableVar(cu.findAll(FieldDeclaration.class), visitMethod,
                insertStmt.getBegin().get().line);
        // 开始匹配

        String name_changed = "";
        List<String> list_avar_key = new ArrayList<String>(availableVar.keySet());
        List<String> list_avar_vale = new ArrayList<String>(availableVar.values());
        NodeList<Parameter> Par = visitMethod.getParameters();
        for (Parameter par_m : Par) {
            list_avar_key.add(par_m.getName().asString());
            list_avar_vale.add(par_m.getType().asString());
        }
        // values 是 类型 key是名称
        List<String> list_need_key = new ArrayList<String>(needfulParam.keySet());
        // values 是 类型 key是名称
        List<String> list_need_vale = new ArrayList<String>(needfulParam.values());
        HashMap<String, String> common = new HashMap<String, String>();
        HashMap<String, String> uncommon = new HashMap<String, String>();
        System.out.println("Start setVarNameInInsertStmt");
        int numms = 0;
        if (availableVar.size() != 0) {
            for (int index = 0; index < list_avar_vale.size(); index++) {
                for (int index_need = 0; index_need < list_need_key.size(); index_need++) {
                    // 判断一下 要是类型已知名字还不一样的话，放在common里，但是只能修改一个啊，总不能都修改了
                    if (list_avar_vale.get(index).equals(list_need_vale.get(index_need))) {
                        if (!list_avar_key.get(index).equals(list_need_key.get(index_need)) && (numms == 0)) {
                            numms++;
                            common.put(list_need_key.get(index_need), list_avar_key.get(index));
                        }

                    } else {
                        if (list_avar_key.get(index).equals(list_need_key.get(index_need))) {
                            // set(旧的，新的)
                            setVarName(insertStmt, list_need_key.get(index_need),
                                    list_need_key.get(index_need) + "_change");
                            uncommon.put(list_need_key.get(index_need) + "_change", list_need_vale.get(index_need));
                        }
                        uncommon.put(list_need_key.get(index_need), list_need_vale.get(index_need));
                    }

                }
            }
        } else {
            System.out.println("uncommon" + needfulParam);
            uncommon = needfulParam;
        }
        System.out.println("common:" + common);
        int common_num = 0;
        for (Map.Entry<String, String> entry_com : common.entrySet()) {
            setVarName(insertStmt, entry_com.getKey(), entry_com.getValue());
            name_changed = entry_com.getKey();
            common_num++;
            break;
        }
        String delete = "";
        uncommon.remove(name_changed);
        if (uncommon.size() != 0) {
            System.out.println("uncommon:\t" + uncommon);
            int num = 0;
            for (Map.Entry<String, String> entry : uncommon.entrySet()) {
                for (int i = 0; i < 6; i++) {
                    // if(!(entry.getValue().equals(typeList.get(i))&&(!entry.getKey().equals(name_changed)))){
                    if (!entry.getValue().equals(typeList.get(i))) {
                        commonType_differName.put(entry.getKey(), entry.getValue());
                    } else {
                        newVarsDec.addAll(callGenerateDec(cu, "myjvm" + num, typeCast.get(i)));
                        newVarsDec.add(StaticJavaParser.parseStatement(entry.getValue() + " " + entry.getKey() + " = "
                                + "(" + typeList.get(i) + ")" + "myjvm" + num + ";\n"));
                        delete = entry.getKey();
                        num++;
                    }
                    commonType_differName.remove(delete);
                }

            }
            commonType_differName.remove(name_changed);
            System.out.println("commonType_differName" + commonType_differName);
            for (Map.Entry<String, String> entry1 : commonType_differName.entrySet()) {
                newVarsDec.addAll(callGenerateDec(cu, entry1.getKey(), entry1.getValue()));
            }
        }

        System.out.println("setVarName" + insertStmt);
        newVarsDec.add(insertStmt);
        return newVarsDec;
    }

    public List<CompilationUnit> FindJava(SourceRoot sourceRoot) throws IOException {
        return sourceRoot.getCompilationUnits();
    }

    public Map<String, String> getAvailableVar(List<FieldDeclaration> fieldList, MethodDeclaration visitMethod,
            int lineIndex) {
        Map<String, String> varMap = new HashMap<String, String>();
        for (FieldDeclaration field : fieldList) {
            for (VariableDeclarator var : field.getVariables()) {
                varMap.put(var.getNameAsString(), var.getTypeAsString());
            }
        }
        List<VariableDeclarator> varList = visitMethod.findAll(VariableDeclarator.class);
        for (VariableDeclarator var : varList) {
            if (var.getBegin().get().line < lineIndex) {
                varMap.put(var.getNameAsString(), var.getTypeAsString());
            }
        }
        return varMap;
    }

    public HashMap<Statement, MethodDeclaration> GetForAndMethodList(String path)
            throws IOException, IllegalArgumentException, SQLException, ClassNotFoundException {
        Path pathToSource = Paths.get(path);
        SourceRoot sourceRoot = new SourceRoot(pathToSource);
        sourceRoot.tryToParse();
        List<CompilationUnit> compilations = FindJava(sourceRoot);
        HashMap<Statement, MethodDeclaration> for_1 = new HashMap<>();
        List<Statement> list = new ArrayList<>();
        List<MethodDeclaration> Method = new ArrayList<>();

        for (CompilationUnit cu : compilations) {
            String name = cu.getStorage().get().getPath().toString();
            String name_1 = cu.getStorage().get().getFileName();
            System.out.println(name);
            List<ClassOrInterfaceDeclaration> c1 = cu.findAll(ClassOrInterfaceDeclaration.class);

            for (ClassOrInterfaceDeclaration c : c1) {

                List<MethodDeclaration> methods = c.getMethods();
                for (MethodDeclaration m : methods) {
                    if (m.getBody().isPresent()) {
                        List<Statement> statementList = m.getBody().get().getStatements();
                        for (Statement stmt : statementList) {
                            // ----------------------------------------判断是不是for语句并且存入到字典中存放格式{for，【变量类型，变量】}------------------------------
                            if (stmt.isForStmt()) {
                                list.add(stmt);
                                Method.add(m);
                                for_1.put(stmt, m);
                            }
                        }
                    }
                }
            }
        }
        return for_1;
    }

    public static void main(String[] args)throws Exception, IllegalArgumentException, FileNotFoundException, NullPointerException, IOException,SQLException, ClassNotFoundException, SQLException, SQLSyntaxErrorException {
        Main mainner = new Main();
        try {
            String pathAll="/root/JVMFuzzing/data/seed/seed_001";
            File file = new File(pathAll);
            ArrayList<String> files = new ArrayList<String>();
            File[] tempList = file.listFiles();
            // 分组的种子程序所在的位置
            String seedpath = "/root/JVMFuzzing/result/ResultOfMutationAndCov1/";
            // 变异的路径
            String filepath = "/root/JVMFuzzing/result/ResultOfMutationAndCov/";
            Path pathToSource = Paths.get(pathAll);
            SourceRoot sourceRoot = new SourceRoot(pathToSource);
            sourceRoot.tryToParse();
            List<CompilationUnit> compilations = mainner.FindJava(sourceRoot);
            System.out.print("here");
            mainner.Insert_Cov(compilations, filepath, seedpath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}