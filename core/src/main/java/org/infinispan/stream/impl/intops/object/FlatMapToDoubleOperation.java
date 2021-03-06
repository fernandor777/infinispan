package org.infinispan.stream.impl.intops.object;

import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.infinispan.reactive.RxJavaInterop;
import org.infinispan.stream.impl.intops.FlatMappingOperation;

import io.reactivex.Flowable;

/**
 * Performs flat map to double operation on a regular {@link Stream}
 * @param <I> the type of the input stream
 */
public class FlatMapToDoubleOperation<I> implements FlatMappingOperation<I, Stream<I>, Double, DoubleStream> {
   private final Function<? super I, ? extends DoubleStream> function;

   public FlatMapToDoubleOperation(Function<? super I, ? extends DoubleStream> function) {
      this.function = function;
   }

   @Override
   public DoubleStream perform(Stream<I> stream) {
      return stream.flatMapToDouble(function);
   }

   public Function<? super I, ? extends DoubleStream> getFunction() {
      return function;
   }

   @Override
   public Stream<DoubleStream> map(Stream<I> iStream) {
      return iStream.map(function);
   }

   @Override
   public Flowable<Double> mapFlowable(Flowable<I> input) {
      return input.flatMap(o -> RxJavaInterop.fromStream(function.apply(o).boxed()));
   }
}
