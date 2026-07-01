package com.fateironist.jawf;

import lombok.extern.slf4j.Slf4j;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

@Slf4j
public class Test1111 {
    class Solution {
        char[] target;
        int[][] map;
        public boolean exist(char[][] board, String word) {
            target = word.toCharArray();
            map = new int[board.length][board[0].length];

            for (int i = 0; i < board.length; i++) {
                for (int j = 0; j < board[0].length; j++) {
                    if (dfs(board, i, j, 0)) {
                        return true;
                    }
                }
            }

            return false;
        }

        public boolean dfs(char[][] board, int x, int y, int idx) {
            if (idx == target.length) {
                return true;
            }
            if (x < 0 || x >= board.length || y < 0 || y >= board[0].length) {
                return false;
            }

            if (map[x][y] == 1) {
                return false;
            }

            if (board[x][y] != target[idx]) {
                return false;
            }

            map[x][y] = 1;
            idx++;

            boolean res = dfs(board, x + 1, y, idx) ||
                    dfs(board, x - 1, y, idx) ||
                    dfs(board, x, y + 1, idx) ||
                    dfs(board, x, y - 1, idx);


            map[x][y] = 0;
            return res;
        }
    }
}
