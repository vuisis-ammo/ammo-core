(defproject netty.lisp "0.1.0-SNAPSHOT"
  :description "This project is to study aspects of the netty.io ByteBuf for use in Ammo"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.antlr/ST4 "4.0.7"]
                 [io.netty/netty-all "4.0.9.Final"]]
  :main netty.lisp
  :profiles {:uberjar {:aot :all}})
