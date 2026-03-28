/*The Problem StatementImagine you have an array of non-negative integers
representing an elevation map where the width of each bar is 1. You need
to calculate how much water it can trap after raining.Water is trapped between
bars whenever there is a "valley"—a low point surrounded by higher bars on both
the left and right. The amount of water above any given bar $i$ is determined by
the shorter of the two tallest bars to its left and right, minus the height of bar $i$ itself.*/
public class WaterTrap {
    public int trap(int[] height) {
        if(height==null||height.length==0){
            return 0;
        }
        int left=0;
        int right=height.length-1;
        int leftMax=0;
        int rightMax=0;
        int totalWater=0;
        while(left<right){
            if(height[left]<height[right]){
                if(height[left]>=leftMax){
                    leftMax=height[left];
                } else {
                    totalWater+=leftMax-height[left];
                }
                left++;
            } else {
                if(height[right]>=rightMax){
                    rightMax=height[right];
                } else {
                    totalWater+=rightMax-height[right];
                }
                right--;
            }
        }
        return totalWater;

    }

    public static void main(String[] args) {
        WaterTrap solver = new WaterTrap();
        int[] elevationMap = {0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1};
        System.out.println("Total water trapped: " + solver.trap(elevationMap));
    }
}