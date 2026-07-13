/**
 * Select — native `<select>` styled to match the system. Used for filter
 * toolbars (project/status/priority/agent) throughout the product.
 *
 * @startingPoint section="Core" subtitle="Filter dropdown" viewport="260x70"
 */
export interface SelectOption {
  value: string;
  label: string;
}
export interface SelectProps {
  value: string;
  onChange: (e: React.ChangeEvent<HTMLSelectElement>) => void;
  options: SelectOption[];
}
