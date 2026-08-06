package com.hjl.oj.utils;

import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;

/**
 * 支持语言的通用初始代码模板。
 */
public final class QuestionStarterCodeDefaults {

    private QuestionStarterCodeDefaults() {
    }

    public static String getDefaultStarterCode(QuestionSubmitLanguageEnum language) {
        return switch (language) {
            case JAVA -> """
                    import java.util.*;
                    
                    public class Main {
                        public static void main(String[] args) {
                            int a = Integer.parseInt(args[0]);
                            System.out.print("output the answer");
                        }
                    }
                    """;
            case CPLUSPLUS -> """
                    #include <iostream>
                    #include <string>
                    using namespace std;
                    
                    int main(int argc, char *argv[]) {
                        int a = stoi(argv[1]);
                        cout << "output the answer";
                        return 0;
                    }
                    """;
            case GOLANG -> """
                    package main
                    
                    import (
                        "fmt"
                        "os"
                        "strconv"
                    )
                    
                    func main() {
                        a, _ := strconv.Atoi(os.Args[1])
                        fmt.Print("output the answer")
                        _ = a
                    }
                    """;
            case PYTHON -> """
                    import sys
                    
                    a = int(sys.argv[1])
                    print("output the answer", end="")
                    """;
            case JAVASCRIPT -> """
                    const a = Number(process.argv[2]);
                    
                    console.log("output the answer");
                    """;
        };
    }
}
