package ru.isa.ai.causal.classifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Author: Aleksandr Panov
 * Date: 30.07.13
 * Time: 16:36
 */
public class TestScanner {
    public static void main(String[] args) {
        String s = "attr_4=4.3..12.000";
        Scanner scanner = new Scanner(s);
        scanner.useLocale(Locale.US);
        scanner.useDelimiter(Pattern.compile("_|=|\\.{2}"));

        System.out.println(scanner.next());
        System.out.println(scanner.nextInt());

        List<Tester> list = new ArrayList<Tester>();
        list.add(new Tester(4));
        list.add(new Tester(5));
        list.add(new Tester(6));
        int c = 5;
        new TestScanner().test(list, c);
        System.out.println(list.get(0).a + " " + c);
        System.out.println(++c);
    }

    private void test(List<Tester> link, int b) {
        link.get(0).a = 7;
    }

    public static class Tester {
        int a = 0;

        public Tester(int a) {
            this.a = a;
        }
    }
}
