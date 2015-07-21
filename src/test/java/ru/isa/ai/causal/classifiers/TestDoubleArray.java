package ru.isa.ai.causal.classifiers;

/**
 * Author: Aleksandr Panov
 * Date: 11.06.2014
 * Time: 10:53
 */
public class TestDoubleArray {
    public static void main(String[] args){
        int[][] test = new int[2][3];
        for(int i = 0; i < test.length; i++)
            for(int j =0; j < test[i].length; j++)
                test[i][j] = (int)(Math.random() * 10);

        for(int i = 0; i < 2; i++)
            for(int j =0; j < 3; j++)
                System.out.println(test[i][j]);
    }
}
