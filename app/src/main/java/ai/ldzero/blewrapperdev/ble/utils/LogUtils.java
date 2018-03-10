package ai.ldzero.blewrapperdev.ble.utils;

/**
 * log工具类
 *
 * Created on 2017/7/22.
 *
 * @author ldzero
 */

public class LogUtils {

    /**
     * 把byte数组转为log string
     *
     * @param array byte数组
     * @return log string
     */
    public static String byteArray2Str(byte[] array) {
        if (array == null) {
            return "null";
        }
        StringBuilder s = new StringBuilder();
        s.append("[ ");
        for (int i = 0; i < array.length - 1; i++) {
            s.append(Integer.toHexString(0xFF & array[i]));
            s.append(" ");
        }
        s.append("]");
        return s.toString();
    }
}
