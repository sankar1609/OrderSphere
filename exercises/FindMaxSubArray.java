public class FindMaxSubArray {
    public static void main(String[] args){
        int[] arr = {1,4,2,9};
        int k=3
        int maxSum=findMaxSumArray(arr,k);
    }
    private int findMaxSumArray(int[] arr, int k){
        int windowSum=0;
        int maxSum=0;
        for(int i=0;i<arr.length;i++){
            windowSum+=arr[i];
            if(i>=k-1){
                maxSum=Math.max(windowSum,maxSum);
                windowSum-=arr[i-(k-1)];
            }
        }
        return maxSum;
    }
}