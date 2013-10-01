(ns netty.lisp
  (:gen-class))

(import '(io.netty.buffer ByteBuf Unpooled) 
        '(java.nio.charset Charset) 
        '(io.netty.util CharsetUtil) )

(defn show-bytebuf
   "This function shows that the capacity and max-capacity are distinct"
   [payload]
   (let (initial (Unpooled/copiedBuffer payload (CharsetUtil/UTF_8)))
        (buf (Unpooled/buffer 10 100)))
   (println "buf:" buf)
   (println "get bool:" (.getBoolean initial 3))
   (println "get byte:" (.getByte initial 3))
   (println "get first char:" (char (.getByte initial 0)))
   (println "add long:" (.writeLong initial 54))
   (println "readable:" (.readableBytes initial))
   (println "writable:" (.writableBytes initial))
   (println "array backed?:" (.hasArray initial))
)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
  (show-bytebuf "a string to put in the buffer"))

