SET extras=info/kgeorgiy/java/advanced/implementor
SET outdir=out/production/java-advanced-2017
SET libs=lib/hamcrest-core-1.3.jar;lib/jsoup-1.8.1.jar;lib/junit-4.11.jar;lib/quickcheck-0.6.jar
SET mysrc=ru/ifmo/ctddev/golchin/implementor
javac -sourcepath java -d out/production/java-advanced-2017 java/%mysrc%/Implementor.java
for %%x in (java/%extras%/*.java) do javac -cp %libs% -sourcepath java -d out/production/java-advanced-2017 java/%extras%/%%x
mkdir temp
jar cvfe temp/implementor.jar ru.ifmo.ctddev.golchin.implementor.Implementor -C %outdir% %mysrc%/Implementor.class -C %outdir% %extras%